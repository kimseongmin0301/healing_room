package com.healingroom.domain.queue.event;

/** 방의 음악 상태가 바뀌었음을 알린다. WebSocket 핸들러가 받아서 방 전체에 새 상태를 보낸다. */
public record QueueChangedEvent(String roomId) {
}
