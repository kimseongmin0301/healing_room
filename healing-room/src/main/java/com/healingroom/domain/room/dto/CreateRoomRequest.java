package com.healingroom.domain.room.dto;

/** 방 만들기 요청 */
public record CreateRoomRequest(String name, String themeId) {
}
