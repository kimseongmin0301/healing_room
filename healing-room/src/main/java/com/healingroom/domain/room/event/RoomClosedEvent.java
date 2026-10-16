package com.healingroom.domain.room.event;

/**
 * 방이 닫혔음을 알린다. 채팅 등 방에 딸린 데이터를 정리하고, 방에 남아 있던 사람에게 알리는 데 쓴다.
 *
 * @param byHost 방장이 닫았으면 true, 오래 비어 있어 닫혔으면 false
 */
public record RoomClosedEvent(String roomId, boolean byHost) {

    public RoomClosedEvent(String roomId) {
        this(roomId, false);
    }
}
