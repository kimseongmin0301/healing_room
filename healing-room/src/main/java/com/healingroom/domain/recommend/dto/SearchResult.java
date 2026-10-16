package com.healingroom.domain.recommend.dto;

/**
 * 직접 검색 결과 하나.
 *
 * @param seconds 영상 길이(초). 알 수 없으면 null
 */
public record SearchResult(String youtubeId, String title, String channel, String thumbnail, Integer seconds) {
}
