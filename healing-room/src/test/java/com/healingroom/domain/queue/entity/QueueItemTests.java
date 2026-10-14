package com.healingroom.domain.queue.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/** 방장의 재생 조작: 위치 = (멈춘 시각 또는 지금) - 시작 시각 */
class QueueItemTests {

    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-14T12:00:00+09:00");

    static double position(QueueItem item, OffsetDateTime now) {
        OffsetDateTime reference = item.getPausedAt() != null ? item.getPausedAt() : now;
        return Duration.between(item.getStartedAt(), reference).toMillis() / 1000.0;
    }

    static QueueItem playing() {
        QueueItem item = new QueueItem("room", "dQw4w9WgXcQ", "제목", "A", 1);
        item.start(T0);
        item.reportDuration(200);
        return item;
    }

    @Test
    void pauseFreezesPositionAndResumeContinuesFromThere() {
        QueueItem item = playing();
        item.pause(T0.plusSeconds(30));
        assertThat(position(item, T0.plusSeconds(90))).isEqualTo(30);  // 멈춘 동안 위치 그대로

        item.resume(T0.plusSeconds(90));
        assertThat(item.isPaused()).isFalse();
        assertThat(position(item, T0.plusSeconds(100))).isEqualTo(40); // 30초에서 이어서 10초 재생
    }

    @Test
    void pausedTrackNeverFinishes() {
        QueueItem item = playing();
        item.pause(T0.plusSeconds(10));
        assertThat(item.isFinished(T0.plusSeconds(1000), Duration.ofSeconds(2))).isFalse();
        item.resume(T0.plusSeconds(1000));
        assertThat(item.isFinished(T0.plusSeconds(1000 + 195), Duration.ofSeconds(2))).isTrue();
    }

    @Test
    void seekMovesPositionWhilePlayingAndWhilePaused() {
        QueueItem item = playing();
        item.seek(120, T0.plusSeconds(10));
        assertThat(position(item, T0.plusSeconds(10))).isEqualTo(120);

        item.pause(T0.plusSeconds(20));                 // 130초에서 멈춤
        item.seek(50, T0.plusSeconds(25));
        assertThat(position(item, T0.plusSeconds(60))).isEqualTo(50); // 멈춘 채 50초로
    }

    @Test
    void reportedDurationKeepsLongestValue() {
        QueueItem item = playing();
        item.reportDuration(5);   // 짧게 속여도
        assertThat(item.getDurationSeconds()).isEqualTo(200);
    }
}
