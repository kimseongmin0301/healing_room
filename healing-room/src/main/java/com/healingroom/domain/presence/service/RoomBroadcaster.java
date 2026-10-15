package com.healingroom.domain.presence.service;

import java.io.IOException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import tools.jackson.databind.json.JsonMapper;

/**
 * 이 서버에 WebSocket 으로 접속해 있는 방 사람들에게 메시지를 보낸다.
 * 받는 사람마다 내용이 다를 수 있어서(내 곡인지, 내 메시지인지) 방문자 id 를 받아 보낼 내용을 만든다.
 */
@Component
public class RoomBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoomBroadcaster.class);

    private final RoomSessionRegistry sessions;
    private final JsonMapper json;

    public RoomBroadcaster(RoomSessionRegistry sessions, JsonMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    /** @param payloadFor 방문자 id → 보낼 객체 (JSON 으로 바꿔 보낸다) */
    public void send(String roomId, Function<String, Object> payloadFor) {
        for (RoomSessionRegistry.Connection connection : sessions.connections(roomId)) {
            try {
                String text = json.writeValueAsString(payloadFor.apply(connection.visitorId()));
                connection.session().sendMessage(new TextMessage(text));
            } catch (IOException | IllegalStateException e) {
                log.debug("전송 실패 (room={}, session={}): {}", roomId, connection.session().getId(), e.getMessage());
            }
        }
    }
}
