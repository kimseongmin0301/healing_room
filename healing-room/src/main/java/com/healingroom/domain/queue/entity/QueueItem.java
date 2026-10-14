package com.healingroom.domain.queue.entity;

import java.time.Duration;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 방 음악 대기열의 곡 하나 (room_queue_item 테이블).
 * addedBy 가 null 이면 대기열이 비어서 자동으로 넣은 기본 음악이다.
 */
@Entity
@Table(name = "room_queue_item")
public class QueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "youtube_id", nullable = false)
    private String youtubeId;

    @Column(nullable = false)
    private String title;

    @Column(name = "added_by")
    private String addedBy;

    @Column(nullable = false)
    private QueueStatus status = QueueStatus.QUEUED;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    protected QueueItem() {
    }

    public QueueItem(String roomId, String youtubeId, String title, String addedBy) {
        this.roomId = roomId;
        this.youtubeId = youtubeId;
        this.title = title;
        this.addedBy = addedBy;
    }

    public static QueueItem defaultTrack(String roomId, String youtubeId, String title) {
        return new QueueItem(roomId, youtubeId, title, null);
    }

    public void start(OffsetDateTime now) {
        status = QueueStatus.PLAYING;
        startedAt = now;
    }

    public void end(QueueStatus endStatus, OffsetDateTime now) {
        status = endStatus;
        endedAt = now;
    }

    /** 플레이어가 알려준 길이. 짧게 속여서 곡을 일찍 끝내지 못하도록 가장 긴 값만 남긴다. */
    public void reportDuration(int seconds) {
        if (durationSeconds == null || seconds > durationSeconds) {
            durationSeconds = seconds;
        }
    }

    /** 길이를 알고 있고, 재생 시작 후 그 시간(+여유)이 지났는지 */
    public boolean isFinished(OffsetDateTime now, Duration grace) {
        return status == QueueStatus.PLAYING && startedAt != null && durationSeconds != null
                && now.isAfter(startedAt.plusSeconds(durationSeconds).plus(grace));
    }

    public boolean isDefault() { return addedBy == null; }
    public boolean isAddedBy(String visitorId) { return addedBy != null && addedBy.equals(visitorId); }

    public Long getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getYoutubeId() { return youtubeId; }
    public String getTitle() { return title; }
    public String getAddedBy() { return addedBy; }
    public QueueStatus getStatus() { return status; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
}
