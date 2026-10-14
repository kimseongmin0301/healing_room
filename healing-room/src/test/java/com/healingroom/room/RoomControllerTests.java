package com.healingroom.room;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.presence.PresenceService;

/** DB·Redis 없이 컨트롤러만 검증한다. */
@WebMvcTest(RoomController.class)
class RoomControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";
    static final RoomResponse CABIN = new RoomResponse("cabin", "불멍", "t", "s", "h", "/images/cabin.webp", "cabin");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RoomService roomService;

    @MockitoBean
    PresenceService presenceService;

    @Test
    void listRooms() throws Exception {
        given(roomService.findAll()).willReturn(List.of(CABIN));

        mvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cabin"))
                .andExpect(jsonPath("$[0].effect").value("cabin"));
    }

    @Test
    void unknownRoomReturns404() throws Exception {
        given(roomService.findById("nope")).willReturn(Optional.empty());

        mvc.perform(get("/api/rooms/nope")).andExpect(status().isNotFound());
    }

    @Test
    void heartbeatReturnsCount() throws Exception {
        given(roomService.exists("cabin")).willReturn(true);
        given(presenceService.heartbeat("cabin", VISITOR)).willReturn(3L);

        mvc.perform(put("/api/rooms/cabin/presence/" + VISITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void heartbeatReturns503WhenRedisFails() throws Exception {
        given(roomService.exists("cabin")).willReturn(true);
        given(presenceService.heartbeat("cabin", VISITOR)).willThrow(new QueryTimeoutException("redis down"));

        mvc.perform(put("/api/rooms/cabin/presence/" + VISITOR)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void heartbeatRejectsNonUuidVisitor() throws Exception {
        mvc.perform(put("/api/rooms/cabin/presence/not-a-uuid")).andExpect(status().isNotFound());
        verify(presenceService, never()).heartbeat(any(), any());
    }
}
