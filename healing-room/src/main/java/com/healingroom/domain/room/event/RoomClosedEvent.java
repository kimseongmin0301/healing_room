package com.healingroom.domain.room.event;

/**
 * 방이 닫혔음을 알린다. 채팅 등 방에 딸린 데이터를 정리하고, 방에 남아 있던 사람에게 알리는 데 쓴다.
 *
 * @param by 누가 닫았는지: {@link #HOST} 방장, {@link #ADMIN} 관리자, {@link #IDLE} 오래 비어 있어서
 */
public record RoomClosedEvent(String roomId, String by) {

    public static final String HOST = "host";
    public static final String ADMIN = "admin";
    public static final String IDLE = "idle";

    public RoomClosedEvent(String roomId) {
        this(roomId, IDLE);
    }
}
