package com.healingroom.domain.presence.handler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.healingroom.domain.chat.service.ChatPolicy;
import com.healingroom.domain.presence.dto.PresencePush;
import com.healingroom.domain.presence.service.RoomBroadcaster;
import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.queue.dto.PlayerState;
import com.healingroom.domain.queue.event.QueueChangedEvent;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.room.service.RoomService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * /ws/rooms/{roomId}?visitorId={uuid}&nickname={닉네임}
 *
 * <p>방에 들어오면 연결하고, 연결돼 있는 동안 "방에 있는 사람"으로 센다.
 * 서버 → 브라우저: 음악 상태({@link PlayerState}, type=state), 채팅(type=chat), 접속자 목록({@link PresencePush}, type=presence).
 * 브라우저 → 서버: 연결 유지용 "ping", 닉네임 변경 {"type":"nickname","nickname":"..."}. 그 밖의 동작은 REST API 로 받는다.
 */
@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private static final String ROOM = "roomId";
    private static final String VISITOR = "visitorId";
    private static final String NICKNAME = "nickname";

    private final RoomSessionRegistry sessions;
    private final RoomBroadcaster broadcaster;
    private final RoomService roomService;
    private final QueueService queueService;
    private final JsonMapper json;

    public RoomSocketHandler(RoomSessionRegistry sessions, RoomBroadcaster broadcaster, RoomService roomService,
                             QueueService queueService, JsonMapper json) {
        this.sessions = sessions;
        this.broadcaster = broadcaster;
        this.roomService = roomService;
        this.queueService = queueService;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
        URI uri = raw.getUri();
        String path = uri == null ? "" : uri.getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);
        var query = uri == null ? null : UriComponentsBuilder.fromUri(uri).build(true).getQueryParams();
        String visitorId = query == null ? null : decode(query.getFirst(VISITOR));
        String nickname = query == null ? null : decode(query.getFirst(NICKNAME));

        if (!isUuid(visitorId) || !roomService.isOpen(roomId)) {
            raw.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 여러 스레드(스케줄러, 요청 스레드, Redis 구독)에서 동시에 보내도 안전하도록 감싼다
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 512 * 1024);
        raw.getAttributes().put(ROOM, roomId);
        raw.getAttributes().put(VISITOR, visitorId);
        String ip = (String) raw.getAttributes().get(ClientIpHandshakeInterceptor.CLIENT_IP);
        sessions.add(roomId, visitorId, session, ip, ChatPolicy.cleanNickname(nickname).orElse("익명"));
        roomService.onPresenceChanged(roomId);
        queueService.onPresenceChanged(roomId); // 여기서 방 전체에 새 음악 상태(인원, 방장)가 나간다
        broadcastPresence(roomId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object roomId = session.getAttributes().get(ROOM);
        if (roomId != null) {
            sessions.remove(roomId.toString(), session.getId());
            roomService.onPresenceChanged(roomId.toString());
            queueService.onPresenceChanged(roomId.toString());
            broadcastPresence(roomId.toString());
        }
    }

    /** "ping"(연결 유지)은 무시하고, 닉네임 변경만 받는다 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        Object roomId = session.getAttributes().get(ROOM);
        Object visitorId = session.getAttributes().get(VISITOR);
        if (roomId == null || visitorId == null || !payload.startsWith("{")) {
            return;
        }
        try {
            JsonNode node = json.readTree(payload);
            if ("nickname".equals(node.path("type").asString(""))) {
                ChatPolicy.cleanNickname(node.path("nickname").asString("")).ifPresent(nickname -> {
                    sessions.setNickname(roomId.toString(), visitorId.toString(), nickname);
                    broadcastPresence(roomId.toString());
                });
            }
        } catch (RuntimeException e) {
            // 형식이 틀린 메시지는 무시한다
        }
    }

    /** 방에 있는 모두에게 접속자 목록을 보낸다 (본인 표시는 받는 사람마다 다르다) */
    private void broadcastPresence(String roomId) {
        var participants = sessions.participants(roomId);
        String hostId = sessions.host(roomId).orElse(null);
        broadcaster.send(roomId, viewerId -> PresencePush.of(participants, hostId, viewerId));
    }

    private static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** 방 음악 상태가 바뀌면 방에 있는 모두에게 각자 기준의 상태를 보낸다. */
    @EventListener
    public void onQueueChanged(QueueChangedEvent event) {
        String roomId = event.roomId();
        if (sessions.connections(roomId).isEmpty()) {
            return;
        }
        PlayerState.Snapshot snapshot = queueService.snapshot(roomId);
        var present = sessions.visitors(roomId);
        String hostId = sessions.host(roomId).orElse(null);
        long now = System.currentTimeMillis();
        broadcaster.send(roomId, visitorId -> snapshot.forVisitor(visitorId, present, hostId, now));
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
