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
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.youtubeId").value("dQw4w9WgXcQ"))
                .andExpect(jsonPath("$.title").value("제목"));
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
    void playbackByNonHostIsForbidden() throws Exception {
        org.mockito.BDDMockito.willThrow(new QueueException.HostOnly()).given(queueService).pause("cabin", VISITOR);

        mvc.perform(put("/api/rooms/cabin/queue/playback")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"pause\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("방장만 할 수 있어요."));
    }

    @Test
    void seekPassesSeconds() throws Exception {
        mvc.perform(put("/api/rooms/cabin/queue/playback")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"seek\",\"seconds\":42.5}"))
                .andExpect(status().isNoContent());
        verify(queueService).seek("cabin", VISITOR, 42.5);
    }

    @Test
    void unknownPlaybackActionIsBadRequest() throws Exception {
        mvc.perform(put("/api/rooms/cabin/queue/playback")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"rewind\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moveUp() throws Exception {
        mvc.perform(put("/api/rooms/cabin/queue/5/move")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direction\":\"up\"}"))
                .andExpect(status().isNoContent());
        verify(queueService).move("cabin", 5L, VISITOR, QueueService.Direction.UP);
    }

    @Test
    void voteReturnsWhetherApplied() throws Exception {
        given(queueService.vote("cabin", 1L, VISITOR)).willReturn(true);

        mvc.perform(put("/api/rooms/cabin/queue/1/vote").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true));
    }
}
