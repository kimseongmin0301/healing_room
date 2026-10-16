package com.healingroom.domain.feedback.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.healingroom.domain.feedback.dto.FeedbackResponse;
import com.healingroom.domain.feedback.entity.Feedback;
import com.healingroom.domain.feedback.exception.FeedbackException;
import com.healingroom.domain.feedback.repository.FeedbackRepository;

/**
 * 로비 의견 보내기. 익명이고, 도배를 막으려고
 * 한 브라우저는 10분에 {@value #PER_VISITOR}번, 사이트 전체는 1시간에 {@value #PER_HOUR_TOTAL}번까지 받는다
 * (방문자 id 는 브라우저가 만드는 값이라 바꿔 가며 보낼 수 있으므로 전체 한도도 둔다). 횟수는 서버 메모리에 둔다.
 */
@Service
public class FeedbackService {

    static final int MAX_LENGTH = 1000;
    static final int PER_VISITOR = 3;
    static final int PER_HOUR_TOTAL = 60;
    private static final Duration VISITOR_WINDOW = Duration.ofMinutes(10);
    private static final Duration TOTAL_WINDOW = Duration.ofHours(1);

    private final FeedbackRepository repository;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byVisitor = new ConcurrentHashMap<>();
    private final Deque<Instant> total = new ArrayDeque<>();

    @Autowired
    public FeedbackService(FeedbackRepository repository) {
        this(repository, Clock.systemUTC());
    }

    FeedbackService(FeedbackRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void send(String visitorId, String message) {
        if (visitorId == null || visitorId.isBlank() || visitorId.length() > 36) {
            throw new FeedbackException.MissingVisitor();
        }
        String text = clean(message);
        if (text.isEmpty() || text.length() > MAX_LENGTH) {
            throw new FeedbackException.InvalidMessage(MAX_LENGTH);
        }
        Instant now = clock.instant();
        Deque<Instant> mine = byVisitor.computeIfAbsent(visitorId, id -> new ArrayDeque<>());
        synchronized (total) {
            synchronized (mine) {
                trim(mine, now.minus(VISITOR_WINDOW));
                trim(total, now.minus(TOTAL_WINDOW));
                if (mine.size() >= PER_VISITOR || total.size() >= PER_HOUR_TOTAL) {
                    throw new FeedbackException.TooMany();
                }
                mine.addLast(now);
                total.addLast(now);
            }
        }
        repository.save(new Feedback(text, visitorId));
    }

    /** 관리자: 최근 의견 (최신순) */
    public List<FeedbackResponse> recent() {
        return repository.findTop200ByOrderByIdDesc().stream().map(FeedbackResponse::from).toList();
    }

    /** 관리자: 장난·도배 의견 지우기 */
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new FeedbackException.NotFound();
        }
        repository.deleteById(id);
    }

    /** 앞뒤 공백과 제어 문자(줄바꿈·탭 제외)를 없앤다. 화면은 textContent 로만 보여 주므로 HTML 은 바꾸지 않는다 */
    static String clean(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").replaceAll("\n{3,}", "\n\n").strip();
    }

    private static void trim(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.pollFirst();
        }
    }

    /** 오래된 기록 정리 (메모리가 계속 늘지 않게) */
    @Scheduled(fixedDelay = 600_000)
    void forgetOld() {
        Instant cutoff = clock.instant().minus(VISITOR_WINDOW);
        byVisitor.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
