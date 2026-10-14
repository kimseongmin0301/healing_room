package com.healingroom.domain.theme.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 테마 (theme 테이블): 방의 배경, 시각 효과, 환경음, 기본 음악의 묶음. 방을 만들 때 하나를 고른다.
 */
@Entity
@Table(name = "theme")
public class Theme {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String subtitle;

    @Column(nullable = false)
    private String hint;

    @Column(nullable = false)
    private String background;

    @Column(name = "background_video")
    private String backgroundVideo;

    @Column(nullable = false)
    private String effect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected Theme() {
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getHint() { return hint; }
    public String getBackground() { return background; }
    public String getBackgroundVideo() { return backgroundVideo; }
    public String getEffect() { return effect; }
    public int getSortOrder() { return sortOrder; }
}
