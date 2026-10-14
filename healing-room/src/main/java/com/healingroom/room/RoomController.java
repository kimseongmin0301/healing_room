package com.healingroom.room;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.presence.PresenceService;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final RoomService roomService;
    private final PresenceService presenceService;

    public RoomController(RoomService roomService, PresenceService presenceService) {
        this.roomService = roomService;
        this.presenceService = presenceService;
    }

    @GetMapping
    public List<RoomResponse> list() {
        return roomService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomResponse> get(@PathVariable String id) {
        return ResponseEntity.of(roomService.findById(id));
    }

    /** 방에 있다는 신호. 브라우저가 주기적으로 호출하며, 현재 인원을 돌려준다. */
    @PutMapping("/{id}/presence/{visitorId}")
    public ResponseEntity<Map<String, Long>> heartbeat(@PathVariable String id, @PathVariable String visitorId) {
        if (!isUuid(visitorId) || !roomService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(Map.of("count", presenceService.heartbeat(id, visitorId)));
        } catch (DataAccessException e) {
            // Redis 장애·권한 문제가 있어도 방 자체는 쓸 수 있도록 인원 표시만 포기한다
            log.warn("presence 갱신 실패 (room={}): {}", id, e.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /** 방을 떠날 때 호출 */
    @DeleteMapping("/{id}/presence/{visitorId}")
    public ResponseEntity<Void> leave(@PathVariable String id, @PathVariable String visitorId) {
        if (isUuid(visitorId)) {
            try {
                presenceService.leave(id, visitorId);
            } catch (DataAccessException e) {
                log.warn("presence 삭제 실패 (room={}): {}", id, e.getMostSpecificCause().getMessage());
            }
        }
        return ResponseEntity.noContent().build();
    }

    // 아무 문자열이나 Redis 에 쌓이지 않도록 방문자 id 는 UUID 형식만 받는다
    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return value.length() == 36;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
