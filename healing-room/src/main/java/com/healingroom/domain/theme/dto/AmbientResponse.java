package com.healingroom.domain.theme.dto;

import com.healingroom.domain.theme.entity.ThemeAmbient;

/** 환경음 믹서의 한 줄 */
public record AmbientResponse(String sound, String label, int defaultVolume) {

    public static AmbientResponse from(ThemeAmbient ambient) {
        return new AmbientResponse(ambient.getSound(), ambient.getLabel(), ambient.getDefaultVolume());
    }
}
