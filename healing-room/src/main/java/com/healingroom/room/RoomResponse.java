package com.healingroom.room;

/**
 * API 로 내려주는 방 정보. 엔티티를 그대로 노출하지 않기 위해 따로 둔다.
 */
public record RoomResponse(
        String id,
        String name,
        String title,
        String subtitle,
        String hint,
        String background,
        String effect
) {

    static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getTitle(), room.getSubtitle(),
                room.getHint(), room.getBackground(), room.getEffect());
    }
}
