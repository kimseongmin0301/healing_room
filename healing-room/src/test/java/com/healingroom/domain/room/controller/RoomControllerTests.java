package com.healingroom.domain.room.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.room.dto.RoomDetailResponse;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.service.RoomService;

/** DB 없이 컨트롤러만 검증한다. */
@WebMvcTest(RoomController.class)
class RoomControllerTests {

    static final RoomResponse CABIN =
            new RoomResponse("cabin", "불멍", "t", "s", "h", "/images/cabin.webp", null, "cabin", 3);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RoomService roomService;

    @Test
    void listRoomsWithListeners() throws Exception {
        given(roomService.findAll()).willReturn(List.of(CABIN));

        mvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cabin"))
                .andExpect(jsonPath("$[0].listeners").value(3));
    }

    @Test
    void roomDetailIncludesAmbients() throws Exception {
        given(roomService.findDetail("cabin")).willReturn(Optional.of(new RoomDetailResponse(CABIN,
                List.of(new RoomDetailResponse.Ambient("fire", "장작 타는 소리", 70)))));

        mvc.perform(get("/api/rooms/cabin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.effect").value("cabin"))
                .andExpect(jsonPath("$.ambients[0].sound").value("fire"))
                .andExpect(jsonPath("$.ambients[0].defaultVolume").value(70));
    }

    @Test
    void unknownRoomReturns404() throws Exception {
        given(roomService.findDetail("nope")).willReturn(Optional.empty());

        mvc.perform(get("/api/rooms/nope")).andExpect(status().isNotFound());
    }
}
