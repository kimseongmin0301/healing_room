package com.healingroom.domain.room.dto;

import java.util.List;

import com.healingroom.domain.theme.dto.AmbientResponse;

/** 방에 들어갈 때 필요한 정보: 방(테마 포함) + 환경음 목록 */
public record RoomDetailResponse(RoomResponse room, List<AmbientResponse> ambients) {
}
