package com.healingroom.domain.chat.dto;

/** WebSocket 으로 보내는 새 채팅 알림 (type=chat) */
public record ChatPush(String type, ChatMessageResponse message) {

    public static ChatPush of(ChatMessageResponse message) {
        return new ChatPush("chat", message);
    }
}
