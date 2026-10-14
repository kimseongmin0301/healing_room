package com.healingroom.domain.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 방마다 믹서에 나오는 환경음 (room_ambient 테이블).
 * sound 는 프론트엔드 static/js/audio/sounds.js 의 키와 같아야 한다.
 */
@Entity
@Table(name = "room_ambient")
public class RoomAmbient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String sound;

    @Column(nullable = false)
    private String label;

    @Column(name = "default_volume", nullable = false)
    private int defaultVolume;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RoomAmbient() {
    }

    public String getRoomId() { return roomId; }
    public String getSound() { return sound; }
    public String getLabel() { return label; }
    public int getDefaultVolume() { return defaultVolume; }
    public int getSortOrder() { return sortOrder; }
}
