package com.healingroom.domain.chat.controller;

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

import com.healingroom.domain.chat.dto.ChatMessageResponse;
import com.healingroom.domain.chat.dto.ChatSendRequest;
import com.healingroom.domain.chat.service.ChatService;
import com.healingroom.domain.moderation.service.ModerationService;
import com.healingroom.global.util.ClientRequest;

import jakarta.servlet.http.HttpServletRequest;

/** 방 채팅 API. 새 메시지는 WebSocket(type=chat)으로도 받는다 */
@RestController
@RequestMapping("/api/rooms/{roomId}/chat")
public class ChatController {

    private final ChatService chatService;

    private final ModerationService moderation;

    public ChatController(ChatService chatService, ModerationService moderation) {
        this.chatService = chatService;
        this.moderation = moderation;
    }

    /** 지난 대화 (오래된 것부터, 최근 100개까지) */
    @GetMapping
    public List<ChatMessageResponse> history(@PathVariable String roomId,
                                             @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return chatService.history(roomId, visitorId);
    }

    /** 보내기 { "nickname": "졸린 고양이", "text": "안녕하세요" } */
    @PostMapping
    public ResponseEntity<ChatMessageResponse> send(@PathVariable String roomId,
                                                    @RequestHeader("X-Visitor-Id") String visitorId,
                                                    @RequestBody ChatSendRequest request,
                                                    HttpServletRequest http) {
        // 욕설·음란어면 30분 제한 (제한 중이어도 403)
        moderation.check(visitorId, ClientRequest.ip(http), "chat", request.nickname(), request.text());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.send(roomId, visitorId, moderation.mask(request.nickname()), moderation.mask(request.text()),
                        request.image())); // 애매한 말은 ** 로
    }
}
