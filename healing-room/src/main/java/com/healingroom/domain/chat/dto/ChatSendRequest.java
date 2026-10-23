package com.healingroom.domain.chat.dto;

/**
 * 채팅 보내기 요청. 로그인이 없어서 닉네임은 브라우저가 정해 함께 보낸다.
 *
 * @param image 붙인 이미지 data URL (없으면 null). 이미지가 있으면 text 는 비어도 된다
 */
public record ChatSendRequest(String nickname, String text, String image) {
}
