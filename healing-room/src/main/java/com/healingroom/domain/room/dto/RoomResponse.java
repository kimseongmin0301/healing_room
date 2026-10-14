package com.healingroom.domain.room.dto;

import com.healingroom.domain.room.entity.Room;

/**
 * 방 목록용 응답. 엔티티를 그대로 노출하지 않기 위해 따로 둔다.
 *
 * @param listeners 지금 방에 접속해 있는 사람 수
 */
public record RoomResponse(
        String id,
        String name,
        String title,
        String subtitle,
        String hint,
        String background,
        String backgroundVideo,
        String effect,
        int listeners
) {

    public static RoomResponse of(Room room, int listeners) {
        return new RoomResponse(room.getId(), room.getName(), room.getTitle(), room.getSubtitle(),
                room.getHint(), room.getBackground(), room.getBackgroundVideo(), room.getEffect(), listeners);
    }
}
