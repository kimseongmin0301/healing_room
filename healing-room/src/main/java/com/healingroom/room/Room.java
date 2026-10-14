package com.healingroom.room;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 힐링룸 한 개 (room 테이블). 테이블 정의는 db/V1__create_room.sql 참고.
 */
@Entity
@Table(name = "room")
public class Room {

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

    @Column(nullable = false)
    private String effect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected Room() {
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getHint() { return hint; }
    public String getBackground() { return background; }
    public String getEffect() { return effect; }
    public int getSortOrder() { return sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
