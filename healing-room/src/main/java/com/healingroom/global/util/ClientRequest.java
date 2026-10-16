package com.healingroom.global.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * nginx 뒤에서 실제 접속 정보를 알아낸다. 요청이 루프백(nginx)에서 왔을 때만 nginx 가 붙여 준 헤더를 믿는다
 * (밖에서 직접 온 요청의 X-Forwarded-* 는 위조할 수 있으므로 무시). WebSocket 쪽은 ClientIpHandshakeInterceptor 가 같은 규칙을 쓴다.
 */
public final class ClientRequest {

    private ClientRequest() {
    }

    public static String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (fromProxy(request) && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    /** https 로 들어온 요청인지 (nginx 가 https 를 받아 http 로 넘겨도 true) */
    public static boolean secure(HttpServletRequest request) {
        return request.isSecure() || (fromProxy(request) && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
    }

    private static boolean fromProxy(HttpServletRequest request) {
        try {
            String remote = request.getRemoteAddr();
            return remote != null && InetAddress.getByName(remote).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
