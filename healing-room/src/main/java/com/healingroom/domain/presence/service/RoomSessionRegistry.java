package com.healingroom.domain.presence.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 방마다 WebSocket 으로 접속해 있는 방문자를 관리한다. "지금 방에 있는 사람"의 기준이다.
 *
 * <p>같은 방문자가 탭을 여러 개 열어도 한 명으로 센다 (visitorId 기준).
 * 서버 메모리에만 있으므로 서버를 여러 대로 늘리면 Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class RoomSessionRegistry {

    public record Connection(String visitorId, WebSocketSession session) {
    }

    // roomId → (sessionId → 접속 정보)
    private final Map<String, Map<String, Connection>> rooms = new ConcurrentHashMap<>();

    public void add(String roomId, String visitorId, WebSocketSession session) {
        rooms.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .put(session.getId(), new Connection(visitorId, session));
    }

    public void remove(String roomId, String sessionId) {
        rooms.computeIfPresent(roomId, (id, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
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
}
