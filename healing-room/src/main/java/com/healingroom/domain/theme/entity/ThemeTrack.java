package com.healingroom.domain.theme.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 테마별 기본 음악 (theme_track 테이블). 방 대기열이 비었을 때 순서대로 돌아가며 재생한다. */
@Entity
@Table(name = "theme_track")
public class ThemeTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_id", nullable = false)
    private String themeId;

    @Column(name = "youtube_id", nullable = false)
    private String youtubeId;

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ThemeTrack() {
    }

    public String getThemeId() { return themeId; }
    public String getYoutubeId() { return youtubeId; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
}
