package com.healingroom.domain.presence.controller;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.presence.exception.PresenceException;
import com.healingroom.domain.presence.service.KickService;

/** DB 없이 컨트롤러만 검증한다. */
@WebMvcTest(KickController.class)
class KickControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KickService kickService;

    @Test
    void hostKicks() throws Exception {
        mvc.perform(post("/api/rooms/sea/kick").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"abc\"}"))
                .andExpect(status().isNoContent());
        verify(kickService).kickByHost("sea", VISITOR, "abc");
    }

    @Test
    void nonHostIs403() throws Exception {
        willThrow(new PresenceException.HostOnly()).given(kickService).kickByHost("sea", VISITOR, "abc");

        mvc.perform(post("/api/rooms/sea/kick").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"abc\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("방장만 내보낼 수 있어요."));
    }
}
