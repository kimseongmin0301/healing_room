package com.healingroom.domain.chat.dto;

/** WebSocket 으로 보내는 "메시지 하나가 지워졌어요" 알림 (type=chat-delete). 받으면 화면에서 그 메시지를 뺀다 */
public record ChatDeletePush(String type, String id) {

    public static ChatDeletePush of(String messageId) {
        return new ChatDeletePush("chat-delete", messageId);
    }
}
