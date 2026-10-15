package com.healingroom.domain.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.admin.dto.AdminPresenceResponse;
import com.healingroom.domain.admin.service.AdminService;

/** 관리자 API. X-Admin-Token 헤더에 ADMIN_TOKEN 값을 넣어야 한다 (화면: /admin.html) */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 모든 방의 접속자 (닉네임, 전체 IP, 접속 시각) */
    @GetMapping("/presence")
    public AdminPresenceResponse presence(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        adminService.authorize(token);
        return adminService.presence();
    }
}
