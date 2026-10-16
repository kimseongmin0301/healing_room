package com.healingroom.domain.room.dto;

import com.healingroom.domain.room.entity.Room;
import com.healingroom.domain.theme.dto.ThemeResponse;

/**
 * 방 목록/상세의 방 정보.
 *
 * @param listeners 지금 방에 접속해 있는 사람 수
 * @param maxPeople 최대 인원 (null 이면 제한 없음)
 */
public record RoomResponse(String id, String name, boolean isDefault, ThemeResponse theme, int listeners, Integer maxPeople) {

    public RoomResponse(String id, String name, boolean isDefault, ThemeResponse theme, int listeners) {
        this(id, name, isDefault, theme, listeners, null);
    }

    public static RoomResponse of(Room room, ThemeResponse theme, int listeners) {
        return new RoomResponse(room.getId(), room.getName(), room.isDefault(), theme, listeners, room.getMaxPeople());
    }
}
