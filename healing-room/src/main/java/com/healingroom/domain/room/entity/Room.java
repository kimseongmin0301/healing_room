package com.healingroom.domain.room.entity;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 방 (room 테이블). 사용자가 테마를 골라 만들고, 아무도 없이 일정 시간이 지나면 닫힌다.
 * 기본 방(isDefault)은 닫히지 않는다.
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    private String id;

    @Column(name = "theme_id", nullable = false)
    private String themeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    /** 로비에 늘 보이기. 아니면 기본 방은 사람이 있을 때만 로비에 나온다 */
    @Column(nullable = false)
    private boolean featured;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 최대 인원 (null 이면 제한 없음) */
    @Column(name = "max_people")
    private Integer maxPeople;

    @Column(name = "emptied_at")
    private OffsetDateTime emptiedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected Room() {
    }

    public Room(String id, String themeId, String name, String createdBy, OffsetDateTime now) {
        this(id, themeId, name, createdBy, null, now);
    }

    public Room(String id, String themeId, String name, String createdBy, Integer maxPeople, OffsetDateTime now) {
        this.id = id;
        this.themeId = themeId;
        this.name = name;
        this.createdBy = createdBy;
        this.maxPeople = maxPeople;
        this.emptiedAt = now; // 만든 사람이 들어오기 전까지는 빈 방
    }

    /** 방장이 바꾸는 방 설정 (이름, 테마, 최대 인원) */
    public void changeSettings(String name, String themeId, Integer maxPeople) {
        this.name = name;
        this.themeId = themeId;
        this.maxPeople = maxPeople;
    }

    /** 사람이 있으면 빈 시각을 지우고, 없으면 처음 빈 시각을 남긴다 */
    public void updateOccupancy(boolean occupied, OffsetDateTime now) {
        if (occupied) {
            emptiedAt = null;
        } else if (emptiedAt == null) {
            emptiedAt = now;
        }
    }

    /** 기본 방이 아니고, idle 동안 계속 비어 있었는지 */
    public boolean isIdleFor(Duration idle, OffsetDateTime now) {
        return !isDefault && closedAt == null && emptiedAt != null && emptiedAt.plus(idle).isBefore(now);
    }

    public void close(OffsetDateTime now) {
        closedAt = now;
    }

    public boolean isOpen() { return closedAt == null; }

    public String getId() { return id; }
    public String getThemeId() { return themeId; }
    public String getName() { return name; }
    public boolean isDefault() { return isDefault; }
    public boolean isFeatured() { return featured; }
    public Integer getMaxPeople() { return maxPeople; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getEmptiedAt() { return emptiedAt; }
}
