package com.healingroom.domain.presence.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *   <li>접속자 목록용으로 방문자마다 닉네임, 접속 IP, 처음 들어온 시각을 함께 기억한다.</li>
 * </ul>
 *
 * <p>서버 메모리에만 있으므로 서버를 여러 대로 늘리면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class RoomSessionRegistry {

    static final long REJOIN_GRACE_MS = 20_000;

    /** 연결(탭) 하나. ip 는 접속한 사람의 IP (nginx 뒤라면 X-Forwarded-For 기준) */
    public record Connection(String visitorId, WebSocketSession session, String ip) {
    }

    /** 방에 있는 사람 한 명 (탭이 여러 개여도 한 명) */
    public record Participant(String visitorId, String nickname, String ip, long joinedAt, int tabs) {
    }

    // roomId → (sessionId → 접속 정보)
    private final Map<String, Map<String, Connection>> rooms = new ConcurrentHashMap<>();
    // roomId → (visitorId → 들어온 순번). 작을수록 먼저 들어온 사람
    private final Map<String, Map<String, Long>> joinOrder = new ConcurrentHashMap<>();
    // roomId → (visitorId → 마지막 연결이 끊긴 시각 ms)
    private final Map<String, Map<String, Long>> leftAt = new ConcurrentHashMap<>();
    // roomId → (visitorId → 처음 들어온 시각 ms, 닉네임)
    private final Map<String, Map<String, Long>> joinedAt = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> nicknames = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public void add(String roomId, String visitorId, WebSocketSession session) {
        add(roomId, visitorId, session, null, null);
    }

    public synchronized void add(String roomId, String visitorId, WebSocketSession session, String ip, String nickname) {
        rooms.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .put(session.getId(), new Connection(visitorId, session, ip));
        forgetStale(roomId);
        joinOrder.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(visitorId, sequence.incrementAndGet());
        joinedAt.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .putIfAbsent(visitorId, System.currentTimeMillis());
        if (nickname != null) {
            setNickname(roomId, visitorId, nickname);
        }
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

    public void setNickname(String roomId, String visitorId, String nickname) {
        nicknames.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>()).put(visitorId, nickname);
    }

    /** 방에 있는 사람 목록 (먼저 들어온 순서). 같은 방문자의 탭 여러 개는 한 명으로 묶는다 */
    public synchronized List<Participant> participants(String roomId) {
        Map<String, Long> order = joinOrder.getOrDefault(roomId, Map.of());
        Map<String, Long> joined = joinedAt.getOrDefault(roomId, Map.of());
        Map<String, String> names = nicknames.getOrDefault(roomId, Map.of());
        Map<String, List<Connection>> byVisitor = connections(roomId).stream()
                .collect(Collectors.groupingBy(Connection::visitorId));
        return byVisitor.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> order.getOrDefault(e.getKey(), Long.MAX_VALUE)))
                .map(e -> new Participant(e.getKey(), names.getOrDefault(e.getKey(), "익명"),
                        e.getValue().stream().map(Connection::ip).filter(Objects::nonNull).findFirst().orElse(null),
                        joined.getOrDefault(e.getKey(), 0L), e.getValue().size()))
                .toList();
    }

    /** 지금 누군가 접속해 있는 방 id 들 */
    public Set<String> activeRooms() {
        return Set.copyOf(rooms.keySet());
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
            if (stale) {
                forget(joinOrder, roomId, entry.getKey());
                forget(joinedAt, roomId, entry.getKey());
                forget(nicknames, roomId, entry.getKey());
            }
            return stale;
        });
    }

    private static void forget(Map<String, ? extends Map<String, ?>> perRoom, String roomId, String visitorId) {
        Map<String, ?> map = perRoom.get(roomId);
        if (map != null) {
            map.remove(visitorId);
        }
    }
}
