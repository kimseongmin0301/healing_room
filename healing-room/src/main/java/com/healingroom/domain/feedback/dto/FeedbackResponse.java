package com.healingroom.domain.feedback.dto;

import java.time.OffsetDateTime;

import com.healingroom.domain.feedback.entity.Feedback;

/**
 * 관리자 화면에 보여줄 의견.
 *
 * @param visitor 보낸 브라우저의 방문자 id 앞 8자리 (같은 사람이 보낸 건지 정도만 구분)
 */
public record FeedbackResponse(Long id, String message, String visitor, OffsetDateTime createdAt) {

    public static FeedbackResponse from(Feedback feedback) {
        String visitorId = feedback.getVisitorId();
        return new FeedbackResponse(feedback.getId(), feedback.getMessage(),
                visitorId.substring(0, Math.min(8, visitorId.length())), feedback.getCreatedAt());
    }
}
