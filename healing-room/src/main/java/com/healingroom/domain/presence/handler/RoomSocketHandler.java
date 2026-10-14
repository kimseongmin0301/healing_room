package com.healingroom.domain.presence.handler;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.json.JsonMapper;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.queue.dto.PlayerState;
import com.healingroom.domain.queue.event.QueueChangedEvent;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.room.service.RoomService;

/**
 * /ws/rooms/{roomId}?visitorId={uuid}
 *
 * <p>방에 들어오면 연결하고, 연결돼 있는 동안 "방에 있는 사람"으로 센다.
 * 서버 → 브라우저로 음악 상태({@link PlayerState})를 보내는 용도이며, 브라우저의 동작(등록·투표)은 REST API 로 받는다.
 */
@Component
public class RoomSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomSocketHandler.class);
    private static final String ROOM = "roomId";
    private static final String VISITOR = "visitorId";

    private final RoomSessionRegistry sessions;
    private final RoomService roomService;
    private final QueueService queueService;
    private final JsonMapper json;

    public RoomSocketHandler(RoomSessionRegistry sessions, RoomService roomService, QueueService queueService,
                             JsonMapper json) {
        this.sessions = sessions;
        this.roomService = roomService;
        this.queueService = queueService;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws IOException {
        URI uri = raw.getUri();
        String path = uri == null ? "" : uri.getPath();
        String roomId = path.substring(path.lastIndexOf('/') + 1);
        String visitorId = uri == null ? null
                : UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(VISITOR);

        if (!isUuid(visitorId) || !roomService.exists(roomId)) {
            raw.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 여러 스레드(스케줄러, 요청 스레드)에서 동시에 보내도 안전하도록 감싼다
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 512 * 1024);
        raw.getAttributes().put(ROOM, roomId);
        raw.getAttributes().put(VISITOR, visitorId);
        sessions.add(roomId, visitorId, session);
        queueService.onPresenceChanged(roomId); // 여기서 방 전체에 새 상태가 나간다
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object roomId = session.getAttributes().get(ROOM);
        if (roomId != null) {
            sessions.remove(roomId.toString(), session.getId());
            queueService.onPresenceChanged(roomId.toString());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 브라우저의 연결 유지용 "ping" 만 받는다. 동작은 REST API 로 처리한다
    }

    /** 방 음악 상태가 바뀌면 방에 있는 모두에게 각자 기준의 상태를 보낸다. */
    @EventListener
    public void onQueueChanged(QueueChangedEvent event) {
        String roomId = event.roomId();
        var connections = sessions.connections(roomId);
        if (connections.isEmpty()) {
            return;
        }
        PlayerState.Snapshot snapshot = queueService.snapshot(roomId);
        var present = sessions.visitors(roomId);
        long now = System.currentTimeMillis();
        for (RoomSessionRegistry.Connection connection : connections) {
            try {
                PlayerState state = snapshot.forVisitor(connection.visitorId(), present, now);
                connection.session().sendMessage(new TextMessage(json.writeValueAsString(state)));
            } catch (IOException | IllegalStateException e) {
                log.debug("상태 전송 실패 (room={}, session={}): {}", roomId, connection.session().getId(), e.getMessage());
            }
        }
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
