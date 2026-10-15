package com.healingroom.domain.room.event;

/** 빈 방이 닫혔음을 알린다. 채팅 등 방에 딸린 데이터를 정리하는 데 쓴다. */
public record RoomClosedEvent(String roomId) {
}
