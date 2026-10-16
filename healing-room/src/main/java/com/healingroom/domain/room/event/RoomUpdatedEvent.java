package com.healingroom.domain.room.event;

/** 방장이 방 설정(이름·테마·최대 인원)을 바꿨음을 알린다. 방에 있는 사람들의 화면을 새로 그리게 한다 */
public record RoomUpdatedEvent(String roomId) {
}
