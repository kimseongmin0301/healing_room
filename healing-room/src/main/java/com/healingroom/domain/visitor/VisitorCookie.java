package com.healingroom.domain.visitor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseCookie;

/**
 * 방문자 id 를 브라우저 저장소(localStorage)뿐 아니라 쿠키(hr_vid)에도 둔다.
 * 내보내진 사람이 저장소만 지우거나 id 를 바꿔도 쿠키가 남아 있으면 같은 사람으로 본다 (로그인 없이 조금 더 단단하게).
 * 쿠키는 자바스크립트로 읽을 수 없고(HttpOnly), 화면은 GET /api/visitor 로 받은 id 를 쓴다.
 */
public final class VisitorCookie {

    public static final String NAME = "hr_vid";
    static final Duration MAX_AGE = Duration.ofDays(400);

    private VisitorCookie() {
    }

    public static ResponseCookie of(String visitorId, boolean secure) {
        return ResponseCookie.from(NAME, visitorId).httpOnly(true).secure(secure).sameSite("Lax").path("/")
                .maxAge(MAX_AGE).build();
    }

    /** "a=1; hr_vid=…; b=2" 같은 Cookie 헤더들에서 방문자 id 를 꺼낸다 (UUID 가 아니면 없음) */
    public static String fromCookieHeaders(List<String> headers) {
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] pair = part.strip().split("=", 2);
                if (pair.length == 2 && NAME.equals(pair[0]) && isUuid(pair[1])) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    public static boolean isUuid(String value) {
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
