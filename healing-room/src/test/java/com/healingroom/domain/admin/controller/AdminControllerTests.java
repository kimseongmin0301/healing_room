package com.healingroom.domain.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.admin.dto.AdminPresenceResponse;
import com.healingroom.domain.admin.exception.AdminException;
import com.healingroom.domain.admin.service.AdminService;
import com.healingroom.domain.room.service.RoomResetService;

@WebMvcTest(AdminController.class)
class AdminControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AdminService adminService;

    @MockitoBean
    RoomResetService resetService;

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
                        new AdminPresenceResponse.Person("졸린 고양이", "203.0.113.10", "3f1c2a9e", true, 1L, 1))))));

        mvc.perform(get("/api/admin/presence").header("X-Admin-Token", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].people[0].ip").value("203.0.113.10"));
    }
}
