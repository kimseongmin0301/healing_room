package com.healingroom.domain.chat.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.chat.dto.ChatMessageResponse;
import com.healingroom.domain.chat.exception.ChatException;
import com.healingroom.domain.chat.service.ChatService;

/** Redis 없이 컨트롤러만 검증한다. */
@WebMvcTest(ChatController.class)
class ChatControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";
    static final String BODY = "{\"nickname\":\"졸린 고양이\",\"text\":\"안녕하세요\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatService chatService;

    @MockitoBean
    com.healingroom.domain.moderation.service.ModerationService moderation;

    @org.junit.jupiter.api.BeforeEach
    void maskReturnsTextAsIs() {
        given(moderation.mask(org.mockito.ArgumentMatchers.any()))
                .willAnswer(org.mockito.AdditionalAnswers.returnsFirstArg());
    }

    @Test
    void profanityIsRefusedWith403AndNotSaved() throws Exception {
        org.mockito.BDDMockito.willThrow(new com.healingroom.domain.moderation.exception.ModerationException.Banned(System.currentTimeMillis()))
                .given(moderation).check(org.mockito.ArgumentMatchers.eq(VISITOR), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("chat"), org.mockito.ArgumentMatchers.eq("졸린 고양이"),
                        org.mockito.ArgumentMatchers.eq("나쁜 말"));
        mvc.perform(post("/api/rooms/cabin/chat").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"졸린 고양이\",\"text\":\"나쁜 말\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("제한")));
        org.mockito.Mockito.verify(chatService, org.mockito.Mockito.never())
                .send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendReturnsMyMessage() throws Exception {
        given(chatService.send("cabin", VISITOR, "졸린 고양이", "안녕하세요", null))
                .willReturn(new ChatMessageResponse("m1", "졸린 고양이", "안녕하세요", 1L, true));

        mvc.perform(post("/api/rooms/cabin/chat").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("안녕하세요"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.authorId").doesNotExist());
    }

    @Test
    void tooFastIs429WithMessage() throws Exception {
        given(chatService.send("cabin", VISITOR, "졸린 고양이", "안녕하세요", null)).willThrow(new ChatException.TooFast());

        mvc.perform(post("/api/rooms/cabin/chat").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("조금 천천히 보내 주세요."));
    }

    @Test
    void notInRoomIs403() throws Exception {
        given(chatService.send("cabin", VISITOR, "졸린 고양이", "안녕하세요", null)).willThrow(new ChatException.NotInRoom());

        mvc.perform(post("/api/rooms/cabin/chat").header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void redisDownIs503() throws Exception {
        given(chatService.history("cabin", VISITOR)).willThrow(new ChatException.Unavailable());

        mvc.perform(get("/api/rooms/cabin/chat").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void history() throws Exception {
        given(chatService.history("cabin", VISITOR)).willReturn(List.of(
                new ChatMessageResponse("m1", "따뜻한 장작", "불 좋다", 1L, false)));

        mvc.perform(get("/api/rooms/cabin/chat").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nickname").value("따뜻한 장작"))
                .andExpect(jsonPath("$[0].mine").value(false));
    }
}
