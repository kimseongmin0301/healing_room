package com.healingroom.domain.room.dto;

import java.util.List;

import com.healingroom.domain.room.entity.RoomAmbient;

/**
 * 방 하나에 들어갈 때 필요한 정보: 방 정보 + 환경음 목록.
 */
public record RoomDetailResponse(RoomResponse room, List<Ambient> ambients) {

    public record Ambient(String sound, String label, int defaultVolume) {

        public static Ambient from(RoomAmbient ambient) {
            return new Ambient(ambient.getSound(), ambient.getLabel(), ambient.getDefaultVolume());
        }
    }
}
