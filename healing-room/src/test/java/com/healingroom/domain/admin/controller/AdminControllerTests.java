package com.healingroom.domain.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.admin.dto.AdminPresenceResponse;
import com.healingroom.domain.admin.exception.AdminException;
import com.healingroom.domain.admin.service.AdminLoginService;
import com.healingroom.domain.admin.service.AdminService;
import com.healingroom.domain.feedback.dto.FeedbackResponse;
import com.healingroom.domain.feedback.service.FeedbackService;
import com.healingroom.domain.room.service.RoomResetService;

@WebMvcTest(AdminController.class)
class AdminControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminService adminService;

    @MockitoBean
    RoomResetService resetService;

    @MockitoBean
    FeedbackService feedbackService;

    @MockitoBean
    AdminLoginService loginService;

    @MockitoBean
    com.healingroom.domain.presence.service.KickService kickService;

    @MockitoBean
    com.healingroom.domain.room.service.RoomService roomService;

    @MockitoBean
    com.healingroom.domain.moderation.service.ModerationService moderation;

    @MockitoBean
    com.healingroom.domain.chat.service.ChatService chatService;

    @Test
    void adminSeesRoomChatAndDeletesOneMessage() throws Exception {
        given(chatService.history("ab3k9xqz", null)).willReturn(List.of(
                new com.healingroom.domain.chat.dto.ChatMessageResponse("m1", "졸린 고양이", "안녕", 1L, false)));
        mvc.perform(get("/api/admin/rooms/ab3k9xqz/chat").header("X-Admin-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("m1"));
        mvc.perform(delete("/api/admin/rooms/ab3k9xqz/chat/m1").header("X-Admin-Token", "secret"))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(chatService).deleteByAdmin("ab3k9xqz", "m1");
    }

    @Test
    void adminSeesAndLiftsBans() throws Exception {
        given(moderation.list()).willReturn(List.of(new com.healingroom.domain.moderation.dto.BanResponse(
                "b1", "chat", "씨발", "11112222", "1.2.3.4", 1L, 2L)));
        mvc.perform(get("/api/admin/bans").header("X-Admin-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ip").value("1.2.3.4"))
                .andExpect(jsonPath("$[0].word").value("씨발"));
        mvc.perform(delete("/api/admin/bans/b1").header("X-Admin-Token", "secret")).andExpect(status().isNoContent());
        org.mockito.Mockito.verify(moderation).lift("b1");
    }

    @Test
    void adminChangesRoomSettings() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/rooms/ab3k9xqz/settings")
                        .header("X-Admin-Token", "secret").contentType("application/json")
                        .content("{\"name\":\"새 이름\",\"themeId\":\"sea\",\"maxPeople\":4}"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(roomService).changeSettingsByAdmin("ab3k9xqz", "새 이름", "sea", 4);
    }

    @Test
    void adminDeletesRoom() throws Exception {
        mvc.perform(delete("/api/admin/rooms/ab3k9xqz").header("X-Admin-Token", "secret"))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(roomService).closeByAdmin("ab3k9xqz");
    }

    @Test
    void defaultRoomCannotBeDeleted() throws Exception {
        willThrow(new com.healingroom.domain.room.exception.RoomException.DefaultRoomLocked())
                .given(roomService).closeByAdmin("sea");
        mvc.perform(delete("/api/admin/rooms/sea").header("X-Admin-Token", "secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roomAdminNeedsToken() throws Exception {
        willThrow(new AdminException.Unauthorized()).given(adminService).authorize(null);
        mvc.perform(delete("/api/admin/rooms/ab3k9xqz")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verify(roomService, org.mockito.Mockito.never()).closeByAdmin(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminKicks() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/kick")
                        .param("roomId", "sea").param("key", "k1").header("X-Admin-Token", "secret"))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(kickService).kickByAdmin("sea", "k1");
    }

    @Test
    void loginSetsSafeCookie() throws Exception {
        given(loginService.login(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("owner"),
                org.mockito.ArgumentMatchers.eq("문장 비밀번호"))).willReturn("session-1");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"문장 비밀번호\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("HR_ADMIN=session-1"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"))));
    }

    @Test
    void wrongLoginIs401() throws Exception {
        given(loginService.login(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).willThrow(new AdminException.WrongLogin());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디나 비밀번호가 맞지 않아요."));
    }

    @Test
    void adminApiNeedsLoginEvenWithToken() throws Exception {
        willThrow(new AdminException.LoginRequired()).given(loginService).require(null);

        mvc.perform(get("/api/admin/presence").header("X-Admin-Token", "secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("먼저 로그인해 주세요."));
        org.mockito.Mockito.verify(adminService, org.mockito.Mockito.never()).presence();
    }

    @Test
    void feedbackListNeedsToken() throws Exception {
        willThrow(new AdminException.Unauthorized()).given(adminService).authorize(null);

        mvc.perform(get("/api/admin/feedback")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(feedbackService);
    }

    @Test
    void adminSeesFeedback() throws Exception {
        given(feedbackService.recent()).willReturn(List.of(
                new FeedbackResponse(7L, "숲 테마 좋아요", "3f1c2a9e", OffsetDateTime.parse("2026-10-16T10:00:00+09:00"))));

        mvc.perform(get("/api/admin/feedback").header("X-Admin-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("숲 테마 좋아요"))
                .andExpect(jsonPath("$[0].visitor").value("3f1c2a9e"));
    }

    @Test
    void adminDeletesFeedback() throws Exception {
        mvc.perform(delete("/api/admin/feedback/7").header("X-Admin-Token", "secret"))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(feedbackService).delete(7L);
    }

    @Test
    void resetAllNeedsToken() throws Exception {
        willThrow(new AdminException.Unauthorized()).given(adminService).authorize(null);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/reset"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(resetService);
    }

    @Test
    void resetOneRoomWithToken() throws Exception {
        given(resetService.reset("sea")).willReturn(java.util.Map.of("room", "sea", "deletedSongs", 3));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/reset")
                        .param("roomId", "sea").header("X-Admin-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedSongs").value(3));
    }

    @Test
    void wrongTokenIs401() throws Exception {
        willThrow(new AdminException.Unauthorized()).given(adminService).authorize("nope");

        mvc.perform(get("/api/admin/presence").header("X-Admin-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledWithoutTokenIs404() throws Exception {
        willThrow(new AdminException.Disabled()).given(adminService).authorize(null);

        mvc.perform(get("/api/admin/presence")).andExpect(status().isNotFound());
    }

    @Test
    void showsFullIpToAdmin() throws Exception {
        given(adminService.presence()).willReturn(new AdminPresenceResponse(1, List.of(
                new AdminPresenceResponse.Room("sea", "바다멍", 1, List.of(
                        new AdminPresenceResponse.Person("k1", "졸린 고양이", "203.0.113.10", "3f1c2a9e", true, 1L, 1))))));

        mvc.perform(get("/api/admin/presence").header("X-Admin-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].people[0].ip").value("203.0.113.10"));
    }
}
