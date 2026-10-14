package com.healingroom.domain.queue.dto;

/** 곡 신청 요청: YouTube 링크(또는 영상 id) */
public record QueueAddRequest(String url) {
}
