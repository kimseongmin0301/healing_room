package com.healingroom.domain.recommend.dto;

/**
 * 추천 영상 하나.
 *
 * @param count 인기곡/함께 신청된 곡일 때 신청된 횟수 (YouTube 검색 결과면 null)
 */
public record VideoSuggestion(String youtubeId, String title, Integer count) {
}
