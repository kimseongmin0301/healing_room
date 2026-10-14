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

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "queue_order", nullable = false)
    private long queueOrder;

    protected QueueItem() {
    }

    /** @param queueOrder 대기열 순서 (방의 마지막 순서 + 1) */
    public QueueItem(String roomId, String youtubeId, String title, String addedBy, long queueOrder) {
        this.roomId = roomId;
        this.youtubeId = youtubeId;
        this.title = title;
        this.addedBy = addedBy;
        this.queueOrder = queueOrder;
    }

    public static QueueItem defaultTrack(String roomId, String youtubeId, String title, long queueOrder) {
        return new QueueItem(roomId, youtubeId, title, null, queueOrder);
    }

    public void start(OffsetDateTime now) {
        status = QueueStatus.PLAYING;
        startedAt = now;
        pausedAt = null;
    }

    public void end(QueueStatus endStatus, OffsetDateTime now) {
        status = endStatus;
        endedAt = now;
        pausedAt = null;
    }

    // ------------------------------------------------ 방장 조작
    // 재생 위치 = (멈춘 시각 또는 지금) - 시작 시각. 멈춤/이동은 시작 시각을 옮겨서 표현한다

    public boolean isPaused() { return pausedAt != null; }

    public void pause(OffsetDateTime now) {
        if (status == QueueStatus.PLAYING && pausedAt == null) {
            pausedAt = now;
        }
    }

    /** 멈춰 있던 시간만큼 시작 시각을 뒤로 미뤄서, 멈춘 위치부터 이어서 재생한다 */
    public void resume(OffsetDateTime now) {
        if (pausedAt != null) {
            startedAt = startedAt.plus(Duration.between(pausedAt, now));
            pausedAt = null;
        }
    }

    /** 재생 위치를 seconds 초로 옮긴다 (멈춰 있으면 멈춘 채로 위치만 바뀐다) */
    public void seek(double seconds, OffsetDateTime now) {
        OffsetDateTime reference = pausedAt != null ? pausedAt : now;
        startedAt = reference.minusNanos((long) (Math.max(0, seconds) * 1_000_000_000L));
    }

    public void swapOrderWith(QueueItem other) {
        long mine = queueOrder;
        queueOrder = other.queueOrder;
        other.queueOrder = mine;
    }

    /** 플레이어가 알려준 길이. 짧게 속여서 곡을 일찍 끝내지 못하도록 가장 긴 값만 남긴다. */
    public void reportDuration(int seconds) {
        if (durationSeconds == null || seconds > durationSeconds) {
            durationSeconds = seconds;
        }
    }

    /** 길이를 알고 있고, 재생 시작 후 그 시간(+여유)이 지났는지 */
    public boolean isFinished(OffsetDateTime now, Duration grace) {
        return status == QueueStatus.PLAYING && pausedAt == null && startedAt != null && durationSeconds != null
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
    public OffsetDateTime getPausedAt() { return pausedAt; }
    public long getQueueOrder() { return queueOrder; }
}
