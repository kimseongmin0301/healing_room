package com.healingroom.domain.queue.dto;

/** 곡 신청 결과. 브라우저가 "내 신청 기록"에 저장할 수 있도록 영상 정보도 돌려준다 */
public record QueueAddResponse(long id, String youtubeId, String title) {
}
