package com.healingroom.domain.theme.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 테마별 환경음 (theme_ambient 테이블).
 * sound 는 프론트엔드 static/js/audio/sounds.js 의 키와 같아야 한다.
 */
@Entity
@Table(name = "theme_ambient")
public class ThemeAmbient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private String themeId;

    @Column(nullable = false)
    private String sound;

    @Column(nullable = false)
    private String label;

    @Column(name = "default_volume", nullable = false)
    private int defaultVolume;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ThemeAmbient() {
    }

    public String getThemeId() { return themeId; }
    public String getSound() { return sound; }
    public String getLabel() { return label; }
    public int getDefaultVolume() { return defaultVolume; }
    public int getSortOrder() { return sortOrder; }
}
