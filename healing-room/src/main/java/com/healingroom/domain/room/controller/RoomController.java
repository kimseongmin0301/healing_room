package com.healingroom.domain.room.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.room.dto.CreateRoomRequest;
import com.healingroom.domain.room.dto.RoomDetailResponse;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.dto.RoomSettingsRequest;
import com.healingroom.domain.room.service.RoomResetService;
import com.healingroom.domain.room.service.RoomService;
import com.healingroom.domain.moderation.service.ModerationService;
import com.healingroom.global.util.ClientRequest;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomResetService resetService;

    private final ModerationService moderation;

    public RoomController(RoomService roomService, RoomResetService resetService, ModerationService moderation) {
        this.roomService = roomService;
        this.resetService = resetService;
        this.moderation = moderation;
    }

    /** 기본 방의 기록 초기화 (그 방의 방장만): 지난 신청곡·투표·통계·채팅을 지우고 기본 음악부터 다시 튼다 */
    @PostMapping("/{id}/reset")
    public ResponseEntity<Void> reset(@PathVariable String id, @RequestHeader("X-Visitor-Id") String visitorId) {
        resetService.resetByHost(id, visitorId);
        return ResponseEntity.noContent().build();
    }

    /** 로비에 보일 방 목록 + 방마다 지금 있는 사람 수 (늘 보이는 방·사용자 방·사람 있는 기본 방) */
    @GetMapping
    public List<RoomResponse> list() {
        return roomService.findLobby();
    }

    /** 방 하나 + 환경음 목록 (방에 들어갈 때 사용). 닫힌 방이면 404 */
    @GetMapping("/{id}")
    public ResponseEntity<RoomDetailResponse> get(@PathVariable String id) {
        return ResponseEntity.of(roomService.findDetail(id));
    }

    /** 방 만들기 { "name": "방 이름", "themeId": "cabin", "maxPeople": 6 } (maxPeople 이 없으면 인원 제한 없음) */
    @PostMapping
    public ResponseEntity<RoomResponse> create(@RequestHeader("X-Visitor-Id") String visitorId,
                                               @RequestBody CreateRoomRequest request, HttpServletRequest http) {
        moderation.check(visitorId, ClientRequest.ip(http), "room-name", request.name()); // 욕설·음란어면 30분 제한
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.create(visitorId, moderation.mask(request.name()), request.themeId(), request.maxPeople()));
    }

    /** 방장: 방 설정 바꾸기 (이름·테마·최대 인원). 기본 방은 403 */
    @PutMapping("/{id}/settings")
    public RoomResponse changeSettings(@PathVariable String id, @RequestHeader("X-Visitor-Id") String visitorId,
                                       @RequestBody RoomSettingsRequest request, HttpServletRequest http) {
        moderation.check(visitorId, ClientRequest.ip(http), "room-name", request.name());
        return roomService.changeSettingsByHost(id, visitorId, moderation.mask(request.name()), request.themeId(), request.maxPeople());
    }

    /** 방장: 방 닫기. 방에 있던 사람들은 로비로 간다. 기본 방은 403 */
    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable String id, @RequestHeader("X-Visitor-Id") String visitorId) {
        roomService.closeByHost(id, visitorId);
        return ResponseEntity.noContent().build();
    }
}
