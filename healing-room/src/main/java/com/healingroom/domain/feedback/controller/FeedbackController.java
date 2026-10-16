package com.healingroom.domain.feedback.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.feedback.dto.FeedbackRequest;
import com.healingroom.domain.feedback.service.FeedbackService;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** 로비에서 의견 보내기 (익명) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void send(@RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                     @RequestBody FeedbackRequest request) {
        feedbackService.send(visitorId, request.message());
    }
}
