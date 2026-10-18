package com.healingroom.domain.visitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

@WebMvcTest(VisitorController.class)
class VisitorControllerTests {

    static final String COOKIE_ID = "11111111-2222-4333-8444-555555555555";
    static final String STORAGE_ID = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";

    @Autowired
    MockMvc mvc;

    @Test
    void cookieWinsOverStorage() throws Exception {
        // 저장소의 id 를 바꿔도 쿠키가 있으면 쿠키 id 를 쓴다 (내보내기 금지를 피하지 못하게)
        mvc.perform(get("/api/visitor").cookie(new Cookie(VisitorCookie.NAME, COOKIE_ID)).header("X-Visitor-Id", STORAGE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COOKIE_ID));
    }

    @Test
    void storageIdIsKeptInCookieWhenNoCookie() throws Exception {
        mvc.perform(get("/api/visitor").header("X-Visitor-Id", STORAGE_ID))
                .andExpect(jsonPath("$.id").value(STORAGE_ID))
                .andExpect(header().string("Set-Cookie", containsString(VisitorCookie.NAME + "=" + STORAGE_ID)))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void apiResponsesCarryAppVersion() throws Exception {
        // 화면은 이 값이 바뀌면(배포) 로비에서 새로고침한다
        mvc.perform(get("/api/visitor")).andExpect(header().exists(com.healingroom.global.config.AppVersionFilter.HEADER));
    }

    @Test
    void newIdWhenNothing() throws Exception {
        mvc.perform(get("/api/visitor")).andExpect(jsonPath("$.id").isString());
    }

    @Test
    void readsCookieHeaderOfWebSocketHandshake() {
        assertThat(VisitorCookie.fromCookieHeaders(List.of("a=1; hr_vid=" + COOKIE_ID + "; b=2"))).isEqualTo(COOKIE_ID);
        assertThat(VisitorCookie.fromCookieHeaders(List.of("hr_vid=not-a-uuid"))).isNull();
        assertThat(VisitorCookie.fromCookieHeaders(null)).isNull();
    }
}
