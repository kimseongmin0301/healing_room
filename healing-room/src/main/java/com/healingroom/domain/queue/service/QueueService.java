package com.healingroom.domain.queue.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.queue.dto.PlayerState;
import com.healingroom.domain.queue.dto.QueueHistoryResponse;
import com.healingroom.domain.queue.entity.QueueItem;
import com.healingroom.domain.queue.entity.QueueSource;
import com.healingroom.domain.queue.entity.QueueStatus;
import com.healingroom.domain.queue.entity.QueueVote;
import com.healingroom.domain.queue.event.QueueChangedEvent;
import com.healingroom.domain.queue.exception.QueueException;
import com.healingroom.domain.queue.repository.QueueItemRepository;
import com.healingroom.domain.queue.repository.QueueVoteRepository;
import com.healingroom.domain.queue.util.SongTitles;
import com.healingroom.domain.recommend.client.YouTubeSearchClient;
import com.healingroom.domain.recommend.dto.VideoSuggestion;
import com.healingroom.domain.recommend.repository.RecommendRepository;
import com.healingroom.domain.room.service.RoomService;
import com.healingroom.domain.theme.entity.ThemeTrack;
import com.healingroom.domain.theme.service.ThemeService;

/**
 * 방마다 함께 듣는 음악 대기열을 관리한다.
 *
 * <ul>
 *   <li>누구나 곡을 신청할 수 있다. 기본 음악이 나오고 있으면 신청곡으로 바로 넘어간다.</li>
 *   <li>내가 신청한 곡은 바로 넘기거나 뺄 수 있다.</li>
 *   <li><b>방장</b>(방에 가장 먼저 들어와 있는 사람)은 모두의 재생을 멈추고/이어 틀고/위치를 옮기고,
 *       아무 곡이나 바로 넘기거나 빼고, 대기열 순서를 바꿀 수 있다.</li>
 *   <li>그 밖의 사람은 방 인원의 50%를 넘게(과반) 투표해야 다른 사람 곡을 넘기거나 뺄 수 있다.</li>
 *   <li>곡이 끝나면 다음 곡. 대기열이 비면 <b>방금 나온 곡과 비슷한 영상을 자동으로 이어 틀고(자동 추천)</b>,
 *       추천을 못 찾으면 테마 기본 음악을 순서대로 튼다. 자동으로 넣은 곡은 누구나 바로 넘길 수 있다.</li>
 * </ul>
 *
 * <p>같은 방의 변경은 방별 잠금으로 한 번에 하나씩 처리한다. 서버가 한 대일 때를 기준으로 한다.
 */
@Service
public class QueueService {

    public enum Direction { UP, DOWN }

    private static final int MAX_NEW_SEARCHES = 2; // 자동 추천 한 번에 새로 YouTube 를 검색하는 횟수 (할당량 아끼기)
    private static final int MAX_SEEDS = 5;        // 자동 추천의 기준 곡 수

    private final QueueItemRepository items;
    private final QueueVoteRepository votes;
    private final RoomService roomService;
    private final ThemeService themeService;
    private final YouTubeSearchClient youtube;
    private final RecommendRepository recommendations;
    private final RoomSessionRegistry sessions;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final Duration endGrace;
    private final int maxQueueSize;

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public QueueService(QueueItemRepository items, QueueVoteRepository votes, RoomService roomService,
                        ThemeService themeService, YouTubeSearchClient youtube, RecommendRepository recommendations,
                        RoomSessionRegistry sessions, TransactionTemplate tx,
                        ApplicationEventPublisher events,
                        @Value("${healing-room.queue.end-grace-seconds}") long endGraceSeconds,
                        @Value("${healing-room.queue.max-size}") int maxQueueSize) {
        this.items = items;
        this.votes = votes;
        this.roomService = roomService;
        this.themeService = themeService;
        this.youtube = youtube;
        this.recommendations = recommendations;
        this.sessions = sessions;
        this.tx = tx;
        this.events = events;
        this.endGrace = Duration.ofSeconds(endGraceSeconds);
        this.maxQueueSize = maxQueueSize;
    }

    // ---------------------------------------------------------------- 조회

    /** 방 전체 음악 상태 (재생 중 곡, 대기열, 투표한 사람) */
    public PlayerState.Snapshot snapshot(String roomId) {
        return tx.execute(status -> {
            QueueItem playing = playing(roomId).orElse(null);
            List<QueueItem> queued = items.findByRoomIdAndStatusOrderByQueueOrderAsc(roomId, QueueStatus.QUEUED);

            List<Long> ids = new ArrayList<>(queued.stream().map(QueueItem::getId).toList());
            if (playing != null) {
                ids.add(playing.getId());
            }
            Map<Long, Set<String>> voters = new HashMap<>();
            for (QueueVote vote : votes.findByItemIdIn(ids)) {
                voters.computeIfAbsent(vote.getItemId(), id -> new HashSet<>()).add(vote.getVisitorId());
            }
            return new PlayerState.Snapshot(playing, queued, voters);
        });
    }

    public PlayerState stateFor(String roomId, String visitorId) {
        return snapshot(roomId).forVisitor(visitorId, sessions.visitors(roomId),
                sessions.host(roomId).orElse(null), System.currentTimeMillis());
    }

    /** 지난 신청곡 (최근 30곡) */
    public List<QueueHistoryResponse> history(String roomId, String visitorId) {
        return tx.execute(status -> items.findTop30ByRoomIdAndAddedByIsNotNullAndStatusInOrderByIdDesc(roomId,
                        List.of(QueueStatus.PLAYED, QueueStatus.SKIPPED, QueueStatus.REMOVED)).stream()
                .map(item -> QueueHistoryResponse.of(item, visitorId))
                .toList());
    }

    // ---------------------------------------------------------------- 누구나

    /** 곡 신청. 영상 확인(oEmbed)은 호출하는 쪽에서 끝낸 뒤 제목과 함께 넘긴다. */
    public long add(String roomId, String visitorId, String youtubeId, String title) {
        requirePresent(roomId, visitorId);
        return inRoom(roomId, () -> {
            if (items.countByRoomIdAndStatus(roomId, QueueStatus.QUEUED) >= maxQueueSize) {
                throw new QueueException.QueueFull();
            }
            QueueItem item = items.save(new QueueItem(roomId, youtubeId, title, visitorId, nextOrder(roomId)));
            QueueItem playing = playing(roomId).orElse(null);
            // 아무것도 안 나오거나 기본 음악이 나오는 중이면 바로 신청곡으로 넘어간다
            if (playing == null || playing.isDefault()) {
                advance(roomId, playing, QueueStatus.PLAYED);
            }
            return item.getId();
        });
    }

    /**
     * 넘기기/빼기. 내가 신청한 곡, 자동으로 넣은 곡(기본 음악·자동 추천)이거나 내가 방장이면 바로 처리하고, 아니면 투표로 센다.
     *
     * @return 곡이 넘겨지거나 빠졌으면 true
     */
    public boolean vote(String roomId, Long itemId, String visitorId) {
        requirePresent(roomId, visitorId);
        return inRoom(roomId, () -> {
            QueueItem item = activeItem(roomId, itemId);
            if (item.isAddedBy(visitorId) || item.isDefault() || sessions.isHost(roomId, visitorId)) {
                drop(roomId, item);
                return true;
            }
            QueueVote.Key key = new QueueVote.Key(itemId, visitorId);
            if (!votes.existsById(key)) {
                votes.save(new QueueVote(itemId, visitorId));
            }
            return decide(roomId, item, sessions.visitors(roomId));
        });
    }

    /** 투표 취소 */
    public void unvote(String roomId, Long itemId, String visitorId) {
        inRoom(roomId, () -> {
            votes.deleteById(new QueueVote.Key(itemId, visitorId));
            return null;
        });
    }

    /** 브라우저 플레이어가 알려준 영상 길이(초). 재생 중인 곡에만 반영한다. */
    public void reportDuration(String roomId, Long itemId, String visitorId, int seconds) {
        requirePresent(roomId, visitorId);
        if (seconds <= 0 || seconds > 6 * 60 * 60) {
            return; // 라이브(0)거나 비정상적인 값
        }
        inRoom(roomId, () -> {
            playing(roomId).filter(item -> item.getId().equals(itemId))
                    .ifPresent(item -> item.reportDuration(seconds));
            return null;
        });
    }

    // ---------------------------------------------------------------- 방장만

    /** 모두의 재생을 멈춘다 */
    public void pause(String roomId, String visitorId) {
        requireHost(roomId, visitorId);
        inRoom(roomId, () -> {
            requirePlaying(roomId).pause(OffsetDateTime.now());
            return null;
        });
    }

    /** 멈춘 위치부터 모두 이어서 재생한다 */
    public void resume(String roomId, String visitorId) {
        requireHost(roomId, visitorId);
        inRoom(roomId, () -> {
            requirePlaying(roomId).resume(OffsetDateTime.now());
            return null;
        });
    }

    /** 모두의 재생 위치를 옮긴다 */
    public void seek(String roomId, String visitorId, double seconds) {
        requireHost(roomId, visitorId);
        inRoom(roomId, () -> {
            QueueItem playing = requirePlaying(roomId);
            Integer duration = playing.getDurationSeconds();
            double target = duration == null ? Math.max(0, seconds) : Math.min(Math.max(0, seconds), duration - 1);
            playing.seek(target, OffsetDateTime.now());
            return null;
        });
    }

    /** 대기열에서 곡을 한 칸 앞/뒤로 옮긴다 */
    public void move(String roomId, Long itemId, String visitorId, Direction direction) {
        requireHost(roomId, visitorId);
        inRoom(roomId, () -> {
            List<QueueItem> queued = items.findByRoomIdAndStatusOrderByQueueOrderAsc(roomId, QueueStatus.QUEUED);
            for (int i = 0; i < queued.size(); i++) {
                if (!queued.get(i).getId().equals(itemId)) {
                    continue;
                }
                int j = direction == Direction.UP ? i - 1 : i + 1;
                if (j >= 0 && j < queued.size()) {
                    queued.get(i).swapOrderWith(queued.get(j));
                }
                return null;
            }
            throw new QueueException.ItemNotFound();
        });
    }

    // ---------------------------------------------------------------- 상황 변화

    // ---------------------------------------------------------------- 기록 초기화

    /**
     * 방의 대기열·지난 신청곡·투표를 모두 지운다 (이 방에서 나온 인기곡·함께 신청된 곡 통계도 함께 사라짐).
     * 사람이 있으면 테마 기본 음악부터 다시 튼다.
     */
    public int resetRoom(String roomId) {
        return inRoom(roomId, () -> {
            int deleted = items.deleteAllInRoom(roomId);
            if (sessions.listenerCount(roomId) > 0) {
                advance(roomId, null, QueueStatus.PLAYED);
            }
            return deleted;
        });
    }

    /** 모든 방의 기록을 지운다. 사람이 있는 방은 기본 음악부터 다시 튼다 */
    public int resetAll() {
        Integer deleted = tx.execute(status -> items.deleteEverything());
        sessions.activeRooms().forEach(this::onPresenceChanged); // 재생 중 곡이 없으니 기본 음악을 시작하고 상태를 알린다
        return deleted == null ? 0 : deleted;
    }

    /** 누가 들어오거나 나갔을 때. 인원(과 방장)이 바뀌면 투표 결과가 달라질 수 있고, 첫 입장이면 음악을 시작한다. */
    public void onPresenceChanged(String roomId) {
        inRoom(roomId, () -> {
            Set<String> present = sessions.visitors(roomId);
            Optional<QueueItem> playing = playing(roomId);
            if (playing.isEmpty()) {
                if (!present.isEmpty()) {
                    advance(roomId, null, QueueStatus.PLAYED);
                }
                return null;
            }
            // 재생 중인 곡이 넘겨지면 대기열도 바뀌므로, 재생 중인 곡부터 차례로 다시 판정한다
            if (!decide(roomId, playing.get(), present)) {
                for (QueueItem queued : items.findByRoomIdAndStatusOrderByQueueOrderAsc(roomId, QueueStatus.QUEUED)) {
                    decide(roomId, queued, present);
                }
            }
            return null;
        });
    }

    /** 2초마다: 길이를 아는 곡 중 끝난 곡이 있으면 다음 곡으로 넘긴다. (멈춘 곡은 끝나지 않는다) */
    @Scheduled(fixedDelay = 2000)
    public void advanceFinished() {
        OffsetDateTime now = OffsetDateTime.now();
        for (QueueItem item : items.findByStatusAndDurationSecondsNotNull(QueueStatus.PLAYING)) {
            if (!item.isFinished(now, endGrace)) {
                continue;
            }
            inRoom(item.getRoomId(), () -> {
                // 잠금을 기다리는 사이 바뀌었을 수 있으니 다시 확인
                playing(item.getRoomId())
                        .filter(current -> current.isFinished(OffsetDateTime.now(), endGrace))
                        .ifPresent(current -> advance(item.getRoomId(), current, QueueStatus.PLAYED));
                return null;
            });
        }
    }

    // ---------------------------------------------------------------- 내부

    /** 지금 방에 있는 사람 기준으로 표가 충분하면 곡을 넘기거나 뺀다. */
    private boolean decide(String roomId, QueueItem item, Set<String> present) {
        long count = votes.findByItemId(item.getId()).stream()
                .filter(vote -> present.contains(vote.getVisitorId()))
                .count();
        if (count > 0 && VotePolicy.passed(count, present.size())) {
            drop(roomId, item);
            return true;
        }
        return false;
    }

    /** 재생 중이면 다음 곡으로 넘기고, 대기 중이면 대기열에서 뺀다. */
    private void drop(String roomId, QueueItem item) {
        if (item.getStatus() == QueueStatus.PLAYING) {
            advance(roomId, item, QueueStatus.SKIPPED);
        } else {
            item.end(QueueStatus.REMOVED, OffsetDateTime.now());
        }
    }

    /** 현재 곡을 끝내고 다음 곡을 시작한다. 대기열이 비었으면 자동 추천(없으면 기본 음악), 방에 아무도 없으면 멈춘다. */
    private void advance(String roomId, QueueItem current, QueueStatus endStatus) {
        OffsetDateTime now = OffsetDateTime.now();
        if (current != null) {
            current.end(endStatus, now);
            items.flush();
        }
        QueueItem next = items.findFirstByRoomIdAndStatusOrderByQueueOrderAsc(roomId, QueueStatus.QUEUED).orElse(null);
        if (next == null) {
            if (sessions.listenerCount(roomId) == 0) {
                return;
            }
            next = nextAutoplay(roomId, current).or(() -> nextDefault(roomId)).orElse(null);
            if (next == null) {
                return;
            }
            next = items.save(next);
        }
        next.start(now);
    }

    /**
     * 자동 추천: 이 방에서 최근 듣던 곡과 비슷한 영상을 고른다. 최근 30곡 안에 나온 곡은 다시 고르지 않고,
     * 영상 id 가 달라도 같은 노래(공식 MV·라이브·가사 영상 등, {@link SongTitles#sameSong})면 고르지 않는다.
     *
     * <p>기준 곡: 방금 끝난 곡 → 이 방에서 최근 신청·자동 추천된 곡들 (기본 음악은 기준으로 쓰지 않는다.
     * 기본 음악 제목으로 찾으면 같은 시리즈만 나와 기본 음악 몇 곡이 계속 돌았다).
     * 기준 곡마다 ① YouTube 비슷한 영상(12시간 캐시) ② 함께 신청된 곡을 보고, 모두 없으면 ③ 테마 인기곡.
     * 그래도 없으면 비어 있다(→ 테마 기본 음악). 새로 YouTube 를 검색하는 건 한 번에 {@value #MAX_NEW_SEARCHES}번까지.
     */
    private Optional<QueueItem> nextAutoplay(String roomId, QueueItem previous) {
        if (previous == null) {
            return Optional.empty();
        }
        List<QueueItem> recentItems = items.findTop30ByRoomIdOrderByIdDesc(roomId);
        Set<String> recent = new HashSet<>();
        List<String> recentTitles = new ArrayList<>();
        recentItems.forEach(item -> {
            recent.add(item.getYoutubeId());
            recentTitles.add(item.getTitle());
        });
        recentTitles.add(previous.getTitle());
        Predicate<VideoSuggestion> fresh = video -> !recent.contains(video.youtubeId())
                && recentTitles.stream().noneMatch(title -> SongTitles.sameSong(title, video.title()));

        int newSearches = 0;
        for (QueueItem seed : autoplaySeeds(previous, recentItems)) {
            List<VideoSuggestion> candidates = new ArrayList<>();
            boolean cached = youtube.hasSimilarCached(seed.getYoutubeId());
            if (cached || newSearches < MAX_NEW_SEARCHES) {
                candidates.addAll(youtube.similarTo(seed.getYoutubeId(), seed.getTitle()));
                if (!cached) {
                    newSearches++;
                }
            }
            candidates.addAll(recommendations.requestedTogether(seed.getYoutubeId(), 3, 10));
            Optional<VideoSuggestion> pick = candidates.stream().filter(fresh).findFirst();
            if (pick.isPresent()) {
                return pick.map(video -> autoplayTrack(roomId, video));
            }
        }
        String themeId = roomService.themeIdOf(roomId).orElse("");
        return recommendations.popularInTheme(themeId, 30, 30).stream().filter(fresh).findFirst()
                .map(video -> autoplayTrack(roomId, video));
    }

    /** 자동 추천의 기준 곡: 방금 끝난 곡, 그다음 최근 신청·자동 추천된 곡 (기본 음악 제외, 같은 영상은 한 번만) */
    static List<QueueItem> autoplaySeeds(QueueItem previous, List<QueueItem> recentItems) {
        Map<String, QueueItem> seeds = new LinkedHashMap<>();
        Stream.concat(Stream.of(previous), recentItems.stream())
                .filter(item -> item.getSource() != QueueSource.DEFAULT)
                .forEach(item -> seeds.putIfAbsent(item.getYoutubeId(), item));
        return seeds.values().stream().limit(MAX_SEEDS).toList();
    }

    private QueueItem autoplayTrack(String roomId, VideoSuggestion video) {
        return QueueItem.systemTrack(roomId, video.youtubeId(), video.title(), nextOrder(roomId), QueueSource.AUTOPLAY);
    }

    /** 테마 기본 음악을 순서대로 돌아가며 고른다. */
    private Optional<QueueItem> nextDefault(String roomId) {
        List<ThemeTrack> list = roomService.themeIdOf(roomId).map(themeService::defaultTracks).orElse(List.of());
        if (list.isEmpty()) {
            return Optional.empty();
        }
        int index = items.findFirstByRoomIdAndSourceOrderByIdDesc(roomId, QueueSource.DEFAULT)
                .map(last -> {
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).getYoutubeId().equals(last.getYoutubeId())) {
                            return (i + 1) % list.size();
                        }
                    }
                    return 0;
                })
                .orElse(0);
        ThemeTrack track = list.get(index);
        return Optional.of(QueueItem.systemTrack(roomId, track.getYoutubeId(), track.getTitle(), nextOrder(roomId),
                QueueSource.DEFAULT));
    }

    private long nextOrder(String roomId) {
        return items.maxQueueOrder(roomId) + 1;
    }

    private Optional<QueueItem> playing(String roomId) {
        return items.findFirstByRoomIdAndStatusOrderByIdAsc(roomId, QueueStatus.PLAYING);
    }

    private QueueItem requirePlaying(String roomId) {
        return playing(roomId).orElseThrow(QueueException.NothingPlaying::new);
    }

    private QueueItem activeItem(String roomId, Long itemId) {
        return items.findById(itemId)
                .filter(item -> item.getRoomId().equals(roomId))
                .filter(item -> item.getStatus() == QueueStatus.QUEUED || item.getStatus() == QueueStatus.PLAYING)
                .orElseThrow(QueueException.ItemNotFound::new);
    }

    private void requirePresent(String roomId, String visitorId) {
        if (visitorId == null || !sessions.isPresent(roomId, visitorId)) {
            throw new QueueException.NotInRoom();
        }
    }

    private void requireHost(String roomId, String visitorId) {
        requirePresent(roomId, visitorId);
        if (!sessions.isHost(roomId, visitorId)) {
            throw new QueueException.HostOnly();
        }
    }

    /** 방별 잠금 안에서 트랜잭션을 실행하고, 끝나면 방 전체에 새 상태를 알린다. */
    private <T> T inRoom(String roomId, Supplier<T> work) {
        ReentrantLock lock = locks.computeIfAbsent(roomId, id -> new ReentrantLock());
        lock.lock();
        try {
            T result = tx.execute(status -> work.get());
            events.publishEvent(new QueueChangedEvent(roomId));
            return result;
        } finally {
            lock.unlock();
        }
    }
}
