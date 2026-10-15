package com.healingroom.domain.admin.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.admin.dto.AdminPresenceResponse;
import com.healingroom.domain.admin.service.AdminService;
import com.healingroom.domain.room.service.RoomResetService;

/** 관리자 API. X-Admin-Token 헤더에 ADMIN_TOKEN 값을 넣어야 한다 (화면: /admin.html) */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final RoomResetService resetService;

    public AdminController(AdminService adminService, RoomResetService resetService) {
        this.adminService = adminService;
        this.resetService = resetService;
    }

    /** 기록 초기화. roomId 가 있으면 그 방만, 없으면 모든 방 (지난 신청곡·투표·통계·채팅·추천 캐시) */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                     @RequestParam(required = false) String roomId) {
        adminService.authorize(token);
        return roomId == null || roomId.isBlank() ? resetService.resetAll() : resetService.reset(roomId);
    }

    /** 모든 방의 접속자 (닉네임, 전체 IP, 접속 시각) */
    @GetMapping("/presence")
    public AdminPresenceResponse presence(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminService.authorize(token);
        return adminService.presence();
    }
}
