package com.healingroom.domain.presence.handler;

import java.net.InetSocketAddress;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 연결을 맺을 때 접속한 사람의 IP 를 세션 속성("clientIp")에 남긴다.
 *
 * <p>운영에서는 nginx 가 앞에 있어 앱이 보는 주소는 127.0.0.1 이다. 그래서 요청이 이 서버 안(루프백)에서 왔을 때만
 * nginx 가 붙여 준 X-Forwarded-For 의 첫 번째 주소를 믿는다. 밖에서 직접 온 요청의 X-Forwarded-For 는 위조할 수 있으므로 무시한다.
 */
public class ClientIpHandshakeInterceptor implements HandshakeInterceptor {

    public static final String CLIENT_IP = "clientIp";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        attributes.put(CLIENT_IP, clientIp(request));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        String direct = remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
        boolean fromProxy = remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (fromProxy && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return direct;
    }
}
