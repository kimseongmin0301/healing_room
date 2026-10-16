package com.healingroom.domain.presence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.presence.dto.KickRequest;
import com.healingroom.domain.presence.service.KickService;

@RestController
public class KickController {

    private final KickService kickService;

    public KickController(KickService kickService) {
        this.kickService = kickService;
    }

    /** 방장이 방에 있는 사람을 내보낸다 (10분 동안 다시 못 들어옴). key 는 접속자 목록의 가린 키 */
    @PostMapping("/api/rooms/{roomId}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@PathVariable String roomId,
                     @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                     @RequestBody KickRequest request) {
        kickService.kickByHost(roomId, visitorId, request.key());
    }
}
