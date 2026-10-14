package com.healingroom.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.healingroom.domain.presence.handler.RoomSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomSocketHandler roomSocketHandler;

    public WebSocketConfig(RoomSocketHandler roomSocketHandler) {
        this.roomSocketHandler = roomSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 허용 출처를 따로 지정하지 않으면 같은 사이트에서 연 페이지만 접속할 수 있다
        registry.addHandler(roomSocketHandler, "/ws/rooms/*");
    }
}
