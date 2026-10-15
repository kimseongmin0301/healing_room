package com.healingroom.domain.chat.dto;

/** WebSocket 으로 보내는 "채팅 기록이 지워졌어요" 알림 (type=chat-clear). 받으면 화면의 대화를 비운다 */
public record ChatClearPush(String type) {

    public static final ChatClearPush INSTANCE = new ChatClearPush("chat-clear");
}
