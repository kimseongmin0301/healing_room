package com.healingroom.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueueLimitsTests {

    @Test
    void acceptsLongCompilationVideos() {
        // 7시간 50분짜리 차트 모음 영상(28186초)의 길이를 버려서, 새로 들어온 사람이 처음부터 듣고 곡도 끝나지 않았다
        assertThat(QueueService.MAX_DURATION_SECONDS).isGreaterThanOrEqualTo(28186);
        assertThat(QueueService.MAX_DURATION_SECONDS).isEqualTo(12 * 60 * 60); // YouTube 최대 업로드 길이
    }
}
