package com.healingroom.domain.queue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 방 기본 음악 (room_playlist 테이블). 대기열이 비었을 때 순서대로 돌아가며 재생한다. */
@Entity
@Table(name = "room_playlist")
public class DefaultTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "youtube_id", nullable = false)
    private String youtubeId;

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected DefaultTrack() {
    }

    public String getRoomId() { return roomId; }
    public String getYoutubeId() { return youtubeId; }
    public String getTitle() { return title; }
    public int getSortOrder() { return sortOrder; }
}
