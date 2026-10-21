package com.healingroom.domain.presence.handler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.healingroom.domain.chat.service.ChatPolicy;
import com.healingroom.domain.moderation.service.ModerationService;
import com.healingroom.domain.presence.dto.PresencePush;
import com.healingroom.domain.presence.service.KickService;
import com.healingroom.domain.presence.service.RoomBroadcaster;
import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.queue.dto.PlayerState;
import com.healingroom.domain.queue.event.QueueChangedEvent;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.room.event.RoomClosedEvent;
import com.healingroom.domain.room.event.RoomUpdatedEvent;
import com.healingroom.domain.room.service.RoomService;
import com.healingroom.domain.visitor.VisitorCookie;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * /ws/rooms/{roomId}?visitorId={uuid}&nickname={닉네임}
 *
 * <p>방에 들어오면 연결하고, 연결돼 있는 동안 "방에 있는 사람"으로 센다.
 * 서버 → 브라우저: 음악 상태({@link PlayerState}, type=state), 채팅(type=chat), 접속자 목록({@link PresencePush}, type=presence),
 * 내보내짐(type=kicked, 4001), 방이 닫힘(type=room-closed, 4002), 방이 꽉 참(type=room-full, 4003), 방 설정 바뀜(type=room-updated).
 * 4001~4003 으로 끊기면 화면은 다시 연결하지 않는다. 쿠키(hr_vid)에 방문자 id 가 있으면 주소의 visitorId 보다 쿠키를 믿는다.
 * 브라우저 → 서버: 연결 유지용 "ping", 닉네임 변경 {"type":"nickname","nickname":"..."}. 그 밖의 동작은 REST API 로 받는다.
 */
@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private static final String ROOM = "roomId";
    private static final String VISITOR = "visitorId";
    private static final String NICKNAME = "nickname";
    /** 방장이 방을 닫아서 / 방이 꽉 차서 끊긴 연결 (화면이 다시 연결하지 않게 구분한다) */
    static final CloseStatus ROOM_CLOSED = new CloseStatus(4002, "room-closed");
    static final CloseStatus ROOM_FULL = new CloseStatus(4003, "room-full");

    private final RoomSessionRegistry sessions;
    private final RoomBroadcaster broadcaster;
    private final RoomService roomService;
    private final QueueService queueService;
    private final KickService kickService;
    private final ModerationService moderation;
    private final JsonMapper json;

    public RoomSocketHandler(RoomSessionRegistry sessions, RoomBroadcaster broadcaster, RoomService roomService,
                             QueueService queueService, KickService kickService, ModerationService moderation,
                             JsonMapper json) {
        this.sessions = sessions;
        this.broadcaster = broadcaster;
        this.roomService = roomService;
        this.queueService = queueService;
        this.kickService = kickService;
        this.moderation = moderation;
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
        // 쿠키에 방문자 id 가 있으면 그것을 믿는다 (저장소의 id 를 바꿔 내보내기 금지를 피하지 못하게)
        String cookieId = VisitorCookie.fromCookieHeaders(raw.getHandshakeHeaders().get("Cookie"));
        if (cookieId != null) {
            visitorId = cookieId;
        }

        if (!isUuid(visitorId) || !roomService.isOpen(roomId)) {
            raw.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 욕설·음란어로 제한 중이거나 닉네임이 욕이면 들어오지 못하게 알리고 끊는다 (닉네임이 욕이면 30분 제한)
        String ip = (String) raw.getAttributes().get(ClientIpHandshakeInterceptor.CLIENT_IP);
        String visitor = visitorId;
        var profane = moderation.bannedUntil(visitor, ip)
                .or(() -> moderation.banIfProfane(visitor, ip, "nickname", nickname));
        if (profane.isPresent()) {
            kickService.refuseProfane(raw, profane.get());
            return;
        }
        // 내보내진 지 10분이 안 됐으면 들어오지 못하게 알리고 끊는다 (다시 두드리면 금지가 지금부터 10분으로 늘어남)
        if (sessions.bannedUntil(roomId, visitorId).isPresent()) {
            kickService.refuse(raw, roomId, visitorId);
            return;
        }
        // 최대 인원이 찼으면 (이미 들어와 있는 사람의 새 탭은 괜찮다) 알리고 끊는다
        Integer max = roomService.maxPeople(roomId).orElse(null);
        if (max != null && !sessions.isPresent(roomId, visitorId) && sessions.listenerCount(roomId) >= max) {
            sendAndClose(raw, Map.of("type", "room-full", "max", max), ROOM_FULL);
            return;
        }
        // 여러 스레드(스케줄러, 요청 스레드, Redis 구독)에서 동시에 보내도 안전하도록 감싼다
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 512 * 1024);
        raw.getAttributes().put(ROOM, roomId);
        raw.getAttributes().put(VISITOR, visitorId);
        sessions.add(roomId, visitorId, session, ip, ChatPolicy.cleanNickname(moderation.mask(nickname)).orElse("익명"));
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
                // 욕설·음란어 닉네임이면 30분 제한 (이 연결을 포함해 모든 방에서 나간다)
                String ip = (String) session.getAttributes().get(ClientIpHandshakeInterceptor.CLIENT_IP);
                if (moderation.banIfProfane(visitorId.toString(), ip, "nickname", node.path("nickname").asString("")).isPresent()) {
                    return;
                }
                ChatPolicy.cleanNickname(moderation.mask(node.path("nickname").asString(""))).ifPresent(nickname -> {
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
        broadcaster.send(roomId, viewerId -> PresencePush.of(participants, hostId, viewerId,
                visitor -> sessions.keyOf(roomId, visitor)));
    }

    private static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** 방장·관리자가 방을 닫으면 방에 있던 모두에게 알리고 끊는다 → 화면은 로비로 가서 알림 창을 띄운다 */
    @TransactionalEventListener(fallbackExecution = true)
    public void onRoomClosed(RoomClosedEvent event) {
        for (var connection : sessions.connections(event.roomId())) {
            sendAndClose(connection.session(), Map.of("type", "room-closed", "by", event.by()), ROOM_CLOSED);
        }
    }

    /** 방장·관리자가 방 설정을 바꾸면 방에 있는 모두에게 알린다 → 화면이 방 정보를 다시 불러 새로 그린다 */
    @TransactionalEventListener(fallbackExecution = true)
    public void onRoomUpdated(RoomUpdatedEvent event) {
        broadcaster.send(event.roomId(), viewerId -> Map.of("type", "room-updated"));
        broadcastPresence(event.roomId());
    }

    private void sendAndClose(WebSocketSession session, Object payload, CloseStatus status) {
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        } catch (IOException | IllegalStateException e) {
            // 이미 끊긴 연결이면 닫기만 한다
        }
        try {
            session.close(status);
        } catch (IOException e) {
            // 무시
        }
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
