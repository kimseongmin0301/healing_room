package com.healingroom.domain.theme.dto;

import com.healingroom.domain.theme.entity.Theme;

/** 테마 정보 (방 만들기 화면, 방 화면에서 사용) */
public record ThemeResponse(
        String id,
        String name,
        String title,
        String subtitle,
        String hint,
        String background,
        String backgroundVideo,
        String effect
) {

    public static ThemeResponse from(Theme theme) {
        return new ThemeResponse(theme.getId(), theme.getName(), theme.getTitle(), theme.getSubtitle(),
                theme.getHint(), theme.getBackground(), theme.getBackgroundVideo(), theme.getEffect());
    }
}
