package com.healingroom.domain.room.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.room.dto.RoomDetailResponse;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.exception.RoomException;
import com.healingroom.domain.room.service.RoomResetService;
import com.healingroom.domain.room.service.RoomService;
import com.healingroom.domain.theme.dto.AmbientResponse;
import com.healingroom.domain.theme.dto.ThemeResponse;

/** DB 없이 컨트롤러만 검증한다. */
@WebMvcTest(RoomController.class)
class RoomControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";
    static final ThemeResponse CABIN_THEME =
            new ThemeResponse("cabin", "불멍", "불멍 · 겨울 오두막", "s", "h", "/images/cabin.webp", null, "cabin");
    static final RoomResponse CABIN = new RoomResponse("cabin", "불멍 · 겨울 오두막", true, CABIN_THEME, 3);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RoomService roomService;

    @MockitoBean
    RoomResetService resetService;

    @MockitoBean
    com.healingroom.domain.moderation.service.ModerationService moderation;

    @org.junit.jupiter.api.BeforeEach
    void maskReturnsTextAsIs() {
        given(moderation.mask(org.mockito.ArgumentMatchers.any()))
                .willAnswer(org.mockito.AdditionalAnswers.returnsFirstArg());
    }

    @Test
    void profaneRoomNameIsRefused() throws Exception {
        org.mockito.BDDMockito.willThrow(new com.healingroom.domain.moderation.exception.ModerationException.Banned(System.currentTimeMillis()))
                .given(moderation).check(org.mockito.ArgumentMatchers.eq(VISITOR), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("room-name"), org.mockito.ArgumentMatchers.eq("나쁜 방"));
        mvc.perform(post("/api/rooms").header("X-Visitor-Id", VISITOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"나쁜 방\",\"themeId\":\"cabin\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(roomService, org.mockito.Mockito.never())
                .create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resetByNonHostIs403() throws Exception {
        org.mockito.BDDMockito.willThrow(new RoomException.ResetNotAllowed()).given(resetService).resetByHost("sea", VISITOR);

        mvc.perform(post("/api/rooms/sea/reset").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("기록 초기화는 기본 방에서 방장만 할 수 있어요."));
    }

    @Test
    void resetByHostIs204() throws Exception {
        mvc.perform(post("/api/rooms/sea/reset").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(resetService).resetByHost("sea", VISITOR);
    }

    @Test
    void listOpenRoomsWithThemeAndListeners() throws Exception {
        given(roomService.findLobby()).willReturn(List.of(CABIN));

        mvc.perform(get("/api/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cabin"))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].theme.effect").value("cabin"))
                .andExpect(jsonPath("$[0].listeners").value(3));
    }

    @Test
    void roomDetailIncludesAmbients() throws Exception {
        given(roomService.findDetail("cabin")).willReturn(Optional.of(new RoomDetailResponse(CABIN,
                List.of(new AmbientResponse("fire", "장작 타는 소리", 70)))));

        mvc.perform(get("/api/rooms/cabin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.theme.background").value("/images/cabin.webp"))
                .andExpect(jsonPath("$.ambients[0].sound").value("fire"))
                .andExpect(jsonPath("$.ambients[0].defaultVolume").value(70));
    }

    @Test
    void closedOrUnknownRoomReturns404() throws Exception {
        given(roomService.findDetail("nope")).willReturn(Optional.empty());

        mvc.perform(get("/api/rooms/nope")).andExpect(status().isNotFound());
    }

    @Test
    void createRoom() throws Exception {
        given(roomService.create(VISITOR, "새벽 작업방", "cabin", 6))
                .willReturn(new RoomResponse("ab3k9xqz", "새벽 작업방", false, CABIN_THEME, 0));

        mvc.perform(post("/api/rooms")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새벽 작업방\",\"themeId\":\"cabin\",\"maxPeople\":6}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ab3k9xqz"))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void hostChangesSettings() throws Exception {
        given(roomService.changeSettingsByHost("ab3k9xqz", VISITOR, "새 이름", "sea", 4))
                .willReturn(new RoomResponse("ab3k9xqz", "새 이름", false, CABIN_THEME, 2, 4));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/rooms/ab3k9xqz/settings")
                        .header("X-Visitor-Id", VISITOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새 이름\",\"themeId\":\"sea\",\"maxPeople\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("새 이름"))
                .andExpect(jsonPath("$.maxPeople").value(4));
    }

    @Test
    void defaultRoomCannotBeClosed() throws Exception {
        org.mockito.BDDMockito.willThrow(new RoomException.DefaultRoomLocked()).given(roomService).closeByHost("sea", VISITOR);

        mvc.perform(post("/api/rooms/sea/close").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("기본 방은 설정을 바꾸거나 닫을 수 없어요."));
    }

    @Test
    void hostClosesRoom() throws Exception {
        mvc.perform(post("/api/rooms/ab3k9xqz/close").header("X-Visitor-Id", VISITOR)).andExpect(status().isNoContent());
        org.mockito.Mockito.verify(roomService).closeByHost("ab3k9xqz", VISITOR);
    }

    @Test
    void createRoomShowsReadableError() throws Exception {
        given(roomService.create(VISITOR, "", "cabin", null)).willThrow(new RoomException.InvalidName(30));

        mvc.perform(post("/api/rooms")
                        .header("X-Visitor-Id", VISITOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"themeId\":\"cabin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("방 이름은 1~30자로 지어 주세요."));
    }
}
