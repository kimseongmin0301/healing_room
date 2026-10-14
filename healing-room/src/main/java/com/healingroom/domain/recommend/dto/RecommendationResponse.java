package com.healingroom.domain.recommend.dto;

import java.util.List;

/**
 * 곡 추천.
 *
 * @param basedOn          추천 기준이 된 곡 제목 (보통 지금 재생 중인 곡). 없으면 null
 * @param similar          YouTube 에서 찾은 비슷한 영상
 * @param similarAvailable YouTube API 키가 설정돼 있는지 (없으면 similar 는 항상 비어 있음)
 * @param together         기준 곡과 같은 방에서 비슷한 때 함께 신청된 곡
 * @param popular          이 테마의 방들에서 최근 30일 동안 많이 신청된 곡
 */
public record RecommendationResponse(
        String basedOn,
        List<VideoSuggestion> similar,
        boolean similarAvailable,
        List<VideoSuggestion> together,
        List<VideoSuggestion> popular
) {
}
