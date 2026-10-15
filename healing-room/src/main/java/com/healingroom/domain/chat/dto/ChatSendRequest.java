package com.healingroom.domain.chat.dto;

/** 채팅 보내기 요청. 로그인이 없어서 닉네임은 브라우저가 정해 함께 보낸다 */
public record ChatSendRequest(String nickname, String text) {
}
