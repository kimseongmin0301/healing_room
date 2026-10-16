package com.healingroom.domain.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.admin.dto.AdminLoginRequest;
import com.healingroom.domain.admin.dto.AdminPresenceResponse;
import com.healingroom.domain.admin.service.AdminLoginService;
import com.healingroom.domain.admin.service.AdminService;
import com.healingroom.domain.feedback.dto.FeedbackResponse;
import com.healingroom.domain.feedback.service.FeedbackService;
import com.healingroom.domain.presence.service.KickService;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.dto.RoomSettingsRequest;
import com.healingroom.domain.room.service.RoomResetService;
import com.healingroom.domain.room.service.RoomService;
import com.healingroom.global.util.ClientRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 API. 두 단계로 막는다: ① 로그인(쿠키 HR_ADMIN) ② 요청마다 X-Admin-Token 헤더에 ADMIN_TOKEN (화면: /admin)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String TOKEN = "X-Admin-Token";

    private final AdminService adminService;
    private final AdminLoginService loginService;
    private final RoomResetService resetService;
    private final FeedbackService feedbackService;
    private final KickService kickService;
    private final RoomService roomService;

    public AdminController(AdminService adminService, AdminLoginService loginService, RoomResetService resetService,
                           FeedbackService feedbackService, KickService kickService, RoomService roomService) {
        this.adminService = adminService;
        this.loginService = loginService;
        this.resetService = resetService;
        this.feedbackService = feedbackService;
        this.kickService = kickService;
        this.roomService = roomService;
    }

    // ---------------------------------------------------------------- 로그인

    /** 아이디·비밀번호가 맞으면 로그인 쿠키를 준다 (자바스크립트로 못 읽고, https 에서만, 다른 사이트에서 보낸 요청엔 안 붙음) */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody AdminLoginRequest body, HttpServletRequest request) {
        String session = loginService.login(ClientRequest.ip(request), body.username(), body.password());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                cookie(session, AdminLoginService.SESSION_TTL.toSeconds(), request).toString()).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                                       HttpServletRequest request) {
        loginService.logout(session);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0, request).toString()).build();
    }

    private static ResponseCookie cookie(String value, long maxAgeSeconds, HttpServletRequest request) {
        return ResponseCookie.from(AdminLoginService.COOKIE, value)
                .httpOnly(true).secure(ClientRequest.secure(request)).sameSite("Strict").path("/")
                .maxAge(maxAgeSeconds).build();
    }

    // ---------------------------------------------------------------- 로그인 + 관리자 키가 있어야 하는 기능

    private void authorize(String session, String token) {
        loginService.require(session);
        adminService.authorize(token);
    }

    /** 기록 초기화. roomId 가 있으면 그 방만, 없으면 모든 방 (지난 신청곡·투표·통계·채팅·추천 캐시) */
    @PostMapping("/reset")
    public Map<String, Object> reset(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                                     @RequestHeader(value = TOKEN, required = false) String token,
                                     @RequestParam(required = false) String roomId) {
        authorize(session, token);
        return roomId == null || roomId.isBlank() ? resetService.resetAll() : resetService.reset(roomId);
    }

    /** 로비에서 받은 의견 (최근 200개, 최신순) */
    @GetMapping("/feedback")
    public List<FeedbackResponse> feedback(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                                           @RequestHeader(value = TOKEN, required = false) String token) {
        authorize(session, token);
        return feedbackService.recent();
    }

    /** 장난·도배 의견 지우기 */
    @DeleteMapping("/feedback/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeedback(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                               @RequestHeader(value = TOKEN, required = false) String token,
                               @PathVariable long id) {
        authorize(session, token);
        feedbackService.delete(id);
    }

    /** 방에서 내보내기 (10분 동안 다시 못 들어옴). key 는 접속자 목록의 가린 키 */
    @PostMapping("/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                     @RequestHeader(value = TOKEN, required = false) String token,
                     @RequestParam String roomId, @RequestParam String key) {
        authorize(session, token);
        kickService.kickByAdmin(roomId, key);
    }

    /** 열려 있는 모든 방 (빈 방·로비에 안 보이는 기본 방 포함) + 테마·인원·최대 인원 */
    @GetMapping("/rooms")
    public List<RoomResponse> rooms(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                                    @RequestHeader(value = TOKEN, required = false) String token) {
        authorize(session, token);
        return roomService.findOpen();
    }

    /** 방 설정 바꾸기 (이름·테마·최대 인원). 기본 방은 테마만 고정. 방 사람들 화면이 바로 바뀐다 */
    @PutMapping("/rooms/{id}/settings")
    public RoomResponse changeRoom(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                                   @RequestHeader(value = TOKEN, required = false) String token,
                                   @PathVariable String id, @RequestBody RoomSettingsRequest body) {
        authorize(session, token);
        return roomService.changeSettingsByAdmin(id, body.name(), body.themeId(), body.maxPeople());
    }

    /** 방 없애기(닫기). 방에 있던 사람은 로비로 가고 채팅도 지워진다. 기본 방은 없앨 수 없다 */
    @DeleteMapping("/rooms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                           @RequestHeader(value = TOKEN, required = false) String token,
                           @PathVariable String id) {
        authorize(session, token);
        roomService.closeByAdmin(id);
    }

    /** 모든 방의 접속자 (닉네임, 전체 IP, 접속 시각) */
    @GetMapping("/presence")
    public AdminPresenceResponse presence(@CookieValue(value = AdminLoginService.COOKIE, required = false) String session,
                                          @RequestHeader(value = TOKEN, required = false) String token) {
        authorize(session, token);
        return adminService.presence();
    }
}
