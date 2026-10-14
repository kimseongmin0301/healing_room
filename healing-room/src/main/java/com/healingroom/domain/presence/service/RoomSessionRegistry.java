package com.healingroom.domain.presence.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 방마다 WebSocket 으로 접속해 있는 방문자와 방장을 관리한다. "지금 방에 있는 사람"의 기준이다.
 *
 * <ul>
 *   <li>같은 방문자가 탭을 여러 개 열어도 한 명으로 센다 (visitorId 기준).</li>
 *   <li>방장 = 지금 방에 있는 사람 중 가장 먼저 들어온 사람. 방장이 나가면 그다음으로 들어온 사람이 방장이 된다.</li>
 *   <li>새로고침처럼 잠깐 끊겼다가 {@link #REJOIN_GRACE_MS} 안에 다시 들어오면 들어온 순서(방장 자리)를 지켜 준다.</li>
 * </ul>
 *
 * <p>서버 메모리에만 있으므로 서버를 여러 대로 늘리면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class RoomSessionRegistry {

    static final long REJOIN_GRACE_MS = 20_000;

    public record Connection(String visitorId, WebSocketSession session) {
    }

    // roomId → (sessionId → 접속 정보)
    private final Map<String, Map<String, Connection>> rooms = new ConcurrentHashMap<>();
    // roomId → (visitorId → 들어온 순번). 작을수록 먼저 들어온 사람
    private final Map<String, Map<String, Long>> joinOrder = new ConcurrentHashMap<>();
    // roomId → (visitorId → 마지막 연결이 끊긴 시각 ms)
    private final Map<String, Map<String, Long>> leftAt = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public synchronized void add(String roomId, String visitorId, WebSocketSession session) {
        rooms.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .put(session.getId(), new Connection(visitorId, session));
        forgetStale(roomId);
        joinOrder.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(visitorId, sequence.incrementAndGet());
        Map<String, Long> left = leftAt.get(roomId);
        if (left != null) {
            left.remove(visitorId);
        }
    }

    public synchronized void remove(String roomId, String sessionId) {
        Map<String, Connection> sessions = rooms.get(roomId);
        if (sessions == null) {
            return;
        }
        Connection removed = sessions.remove(sessionId);
        if (removed != null && sessions.values().stream().noneMatch(c -> c.visitorId().equals(removed.visitorId()))) {
            leftAt.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                    .put(removed.visitorId(), System.currentTimeMillis());
        }
        if (sessions.isEmpty()) {
            rooms.remove(roomId);
        }
    }

    /** 방장 방문자 id. 방에 아무도 없으면 비어 있다 */
    public synchronized Optional<String> host(String roomId) {
        Map<String, Long> order = joinOrder.getOrDefault(roomId, Map.of());
        return visitors(roomId).stream()
                .min(Comparator.comparingLong(visitor -> order.getOrDefault(visitor, Long.MAX_VALUE)));
    }

    public boolean isHost(String roomId, String visitorId) {
        return visitorId != null && host(roomId).map(visitorId::equals).orElse(false);
    }

    /** 방에 있는 방문자 id (중복 없음) */
    public Set<String> visitors(String roomId) {
        return connections(roomId).stream().map(Connection::visitorId).collect(Collectors.toUnmodifiableSet());
    }

    public int listenerCount(String roomId) {
        return visitors(roomId).size();
    }

    public boolean isPresent(String roomId, String visitorId) {
        return connections(roomId).stream().anyMatch(c -> c.visitorId().equals(visitorId));
    }

    public Collection<Connection> connections(String roomId) {
        Map<String, Connection> sessions = rooms.get(roomId);
        return sessions == null ? List.of() : List.copyOf(sessions.values());
    }

    /** 나간 지 오래된 사람의 순번을 지운다. 다시 들어오면 맨 뒤 순서가 된다 */
    private void forgetStale(String roomId) {
        Map<String, Long> left = leftAt.get(roomId);
        if (left == null) {
            return;
        }
        long now = System.currentTimeMillis();
        left.entrySet().removeIf(entry -> {
            boolean stale = now - entry.getValue() > REJOIN_GRACE_MS;
            Map<String, Long> order = joinOrder.get(roomId);
            if (stale && order != null) {
                order.remove(entry.getKey());
            }
            return stale;
        });
    }
}
