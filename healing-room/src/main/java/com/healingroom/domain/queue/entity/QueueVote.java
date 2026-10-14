package com.healingroom.domain.queue.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** 곡 넘기기/빼기 투표 한 표 (room_queue_vote 테이블) */
@Entity
@Table(name = "room_queue_vote")
@IdClass(QueueVote.Key.class)
public class QueueVote {

    public record Key(Long itemId, String visitorId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Id
    @Column(name = "visitor_id")
    private String visitorId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected QueueVote() {
    }

    public QueueVote(Long itemId, String visitorId) {
        this.itemId = itemId;
        this.visitorId = visitorId;
    }

    public Long getItemId() { return itemId; }
    public String getVisitorId() { return visitorId; }
}
