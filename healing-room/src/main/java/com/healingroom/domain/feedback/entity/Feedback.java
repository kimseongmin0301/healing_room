package com.healingroom.domain.feedback.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 로비에서 받은 의견 하나 (feedback 테이블) */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    @Column(name = "visitor_id", nullable = false)
    private String visitorId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Feedback() {
    }

    public Feedback(String message, String visitorId) {
        this.message = message;
        this.visitorId = visitorId;
    }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public String getVisitorId() { return visitorId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
