package com.healingroom.domain.room.dto;

import com.healingroom.domain.room.entity.Room;
import com.healingroom.domain.theme.dto.ThemeResponse;

/**
 * 방 목록/상세의 방 정보.
 *
 * @param listeners 지금 방에 접속해 있는 사람 수
 */
public record RoomResponse(String id, String name, boolean isDefault, ThemeResponse theme, int listeners) {

    public static RoomResponse of(Room room, ThemeResponse theme, int listeners) {
        return new RoomResponse(room.getId(), room.getName(), room.isDefault(), theme, listeners);
    }
}
