package com.healingroom.domain.room.dto;

/** 방장이 바꾸는 방 설정. maxPeople 이 null 이면 인원 제한 없음 */
public record RoomSettingsRequest(String name, String themeId, Integer maxPeople) {
}
