package com.healingroom.domain.recommend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.recommend.dto.RecommendationResponse;
import com.healingroom.domain.recommend.service.RecommendService;

@RestController
@RequestMapping("/api/rooms/{roomId}/recommendations")
public class RecommendController {

    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    /** 지금 재생 중인 곡을 기준으로 한 추천 (비슷한 영상, 함께 신청된 곡, 테마 인기곡) */
    @GetMapping
    public RecommendationResponse recommend(@PathVariable String roomId) {
        return recommendService.recommend(roomId);
    }
}
