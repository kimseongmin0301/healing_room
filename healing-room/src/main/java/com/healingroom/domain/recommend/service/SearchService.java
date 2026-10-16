package com.healingroom.domain.recommend.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.recommend.client.YouTubeSearchClient;
import com.healingroom.domain.recommend.dto.SearchResult;
import com.healingroom.domain.recommend.exception.SearchException;

/**
 * 곡 직접 검색. 방에 접속해 있는 사람만, 한 사람이 1분에 per-minute 번까지 검색할 수 있다
 * (검색 1번이 하루 할당량 10,000 중 100을 쓰고, 그 할당량을 자동 추천과 함께 쓰므로).
 * 검색 횟수는 서버 메모리에 둔다 (서버는 한 대).
 */
@Service
public class SearchService {

    static final int MAX_QUERY = 100;
    private static final long WINDOW_SECONDS = 60;

    private final YouTubeSearchClient youtube;
    private final RoomSessionRegistry sessions;
    private final int perMinute;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recentSearches = new ConcurrentHashMap<>();

    @Autowired
    public SearchService(YouTubeSearchClient youtube, RoomSessionRegistry sessions,
                         @Value("${healing-room.youtube.search-per-minute}") int perMinute) {
        this(youtube, sessions, perMinute, Clock.systemUTC());
    }

    SearchService(YouTubeSearchClient youtube, RoomSessionRegistry sessions, int perMinute, Clock clock) {
        this.youtube = youtube;
        this.sessions = sessions;
        this.perMinute = perMinute;
        this.clock = clock;
    }

    public List<SearchResult> search(String roomId, String visitorId, String query) {
        if (visitorId == null || !sessions.isPresent(roomId, visitorId)) {
            throw new SearchException.NotInRoom();
        }
        String q = query == null ? "" : query.strip();
        if (q.isEmpty() || q.length() > MAX_QUERY) {
            throw new SearchException.InvalidQuery(MAX_QUERY);
        }
        if (!youtube.isAvailable()) {
            throw new SearchException.Unavailable();
        }
        acquire(visitorId);
        return youtube.search(q);
    }

    /** 1분 안에 perMinute 번을 넘으면 거절 */
    private void acquire(String visitorId) {
        Instant now = clock.instant();
        Deque<Instant> times = recentSearches.computeIfAbsent(visitorId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minusSeconds(WINDOW_SECONDS))) {
                times.pollFirst();
            }
            if (times.size() >= perMinute) {
                throw new SearchException.TooMany(perMinute);
            }
            times.addLast(now);
        }
    }

    /** 오래된 검색 기록 정리 (메모리가 계속 늘지 않게) */
    @Scheduled(fixedDelay = 600_000)
    void forgetOld() {
        Instant cutoff = clock.instant().minusSeconds(WINDOW_SECONDS);
        recentSearches.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
