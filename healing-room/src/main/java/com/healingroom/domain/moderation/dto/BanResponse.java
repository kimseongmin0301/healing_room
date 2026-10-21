package com.healingroom.domain.moderation.dto;

/**
 * 관리자 화면: 욕설·음란어로 이용이 제한된 사람.
 *
 * @param where   어디서 걸렸는지 (chat 채팅, nickname 닉네임, room-name 방 이름)
 * @param word    걸린 말 (오탐인지 보려고. 쓴 글 전체는 두지 않는다)
 * @param visitor 방문자 번호 앞 8자리
 * @param ip      전체 IP (관리자만 봄)
 */
public record BanResponse(String id, String where, String word, String visitor, String ip, long at, long until) {
}
