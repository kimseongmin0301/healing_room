package com.healingroom.domain.room.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.room.dto.CreateRoomRequest;
import com.healingroom.domain.room.dto.RoomDetailResponse;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.service.RoomService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /** 열려 있는 방 목록 + 방마다 지금 있는 사람 수 (로비에서 사용) */
    @GetMapping
    public List<RoomResponse> list() {
        return roomService.findOpen();
    }

    /** 방 하나 + 환경음 목록 (방에 들어갈 때 사용). 닫힌 방이면 404 */
    @GetMapping("/{id}")
    public ResponseEntity<RoomDetailResponse> get(@PathVariable String id) {
        return ResponseEntity.of(roomService.findDetail(id));
    }

    /** 방 만들기 { "name": "방 이름", "themeId": "cabin" } */
    @PostMapping
    public ResponseEntity<RoomResponse> create(@RequestHeader("X-Visitor-Id") String visitorId,
                                               @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roomService.create(visitorId, request.name(), request.themeId()));
    }
}
