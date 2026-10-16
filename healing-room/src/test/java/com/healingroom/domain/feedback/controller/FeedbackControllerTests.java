package com.healingroom.domain.feedback.controller;

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

import com.healingroom.domain.feedback.exception.FeedbackException;
import com.healingroom.domain.feedback.service.FeedbackService;

/** DB 없이 컨트롤러만 검증한다. */
@WebMvcTest(FeedbackController.class)
class FeedbackControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FeedbackService feedbackService;

    @Test
    void sendFeedback() throws Exception {
        mvc.perform(post("/api/feedback").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"숲 테마 좋아요\"}"))
                .andExpect(status().isCreated());
        verify(feedbackService).send(VISITOR, "숲 테마 좋아요");
    }

    @Test
    void tooManyIs429() throws Exception {
        willThrow(new FeedbackException.TooMany()).given(feedbackService).send(VISITOR, "또");

        mvc.perform(post("/api/feedback").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"또\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("의견을 여러 번 보냈어요. 잠시 후 다시 보내 주세요."));
    }
}
