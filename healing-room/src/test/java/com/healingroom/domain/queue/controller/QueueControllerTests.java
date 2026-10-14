package com.healingroom.domain.queue.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.queue.client.YouTubeOEmbedClient;
import com.healingroom.domain.queue.exception.QueueException;
import com.healingroom.domain.queue.service.QueueService;

/** DB·YouTube 없이 컨트롤러만 검증한다. */
@WebMvcTest(QueueController.class)
class QueueControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    QueueService queueService;

    @MockitoBean
    YouTubeOEmbedClient oEmbed;

    @Test
    void addParsesLinkAndFetchesTitle() throws Exception {
        given(oEmbed.fetchTitle("dQw4w9WgXcQ")).willReturn("제목");
        given(queueService.add("cabin", VISITOR, "dQw4w9WgXcQ", "제목")).willReturn(7L);

        mvc.perform(post("/api/rooms/cabin/queue")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://youtu.be/dQw4w9WgXcQ?si=abc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void addRejectsInvalidLinkWithMessage() throws Exception {
        mvc.perform(post("/api/rooms/cabin/queue")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/video\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        verify(oEmbed, never()).fetchTitle(any());
    }

    @Test
    void voteWhenNotInRoomIsForbidden() throws Exception {
        given(queueService.vote("cabin", 1L, VISITOR)).willThrow(new QueueException.NotInRoom());

        mvc.perform(put("/api/rooms/cabin/queue/1/vote").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void voteReturnsWhetherApplied() throws Exception {
        given(queueService.vote("cabin", 1L, VISITOR)).willReturn(true);

        mvc.perform(put("/api/rooms/cabin/queue/1/vote").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));
    }
}
