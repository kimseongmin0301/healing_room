package com.healingroom.domain.room.dto;

/** 방 만들기 요청. maxPeople 이 null 이면 인원 제한 없음 */
public record CreateRoomRequest(String name, String themeId, Integer maxPeople) {
}
