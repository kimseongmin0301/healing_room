package com.healingroom.domain.room.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/** 빈 방 닫기: 아무도 없는 채로 30분이 지나야 닫힌다 */
class RoomTests {

    static final Duration IDLE = Duration.ofMinutes(30);
    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-14T12:00:00+09:00");

    @Test
    void closesOnlyAfterBeingEmptyForIdleTime() {
        Room room = new Room("abcd2345", "cabin", "새벽 작업방", "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11", T0);
        room.updateOccupancy(true, T0);               // 사람이 들어옴
        room.updateOccupancy(false, T0.plusMinutes(10)); // 마지막 사람이 나감
        assertThat(room.isIdleFor(IDLE, T0.plusMinutes(39))).isFalse();
        assertThat(room.isIdleFor(IDLE, T0.plusMinutes(41))).isTrue();
    }

    @Test
    void someoneComingBackResetsTheTimer() {
        Room room = new Room("abcd2345", "cabin", "새벽 작업방", null, T0);
        room.updateOccupancy(false, T0);
        room.updateOccupancy(true, T0.plusMinutes(29));   // 닫히기 직전에 누가 들어옴
        room.updateOccupancy(false, T0.plusMinutes(31));
        assertThat(room.isIdleFor(IDLE, T0.plusMinutes(55))).isFalse();
        assertThat(room.isIdleFor(IDLE, T0.plusMinutes(62))).isTrue();
    }

    @Test
    void stillEmptyDoesNotMoveTheStartOfEmptiness() {
        Room room = new Room("abcd2345", "cabin", "새벽 작업방", null, T0);
        room.updateOccupancy(false, T0);
        room.updateOccupancy(false, T0.plusMinutes(20)); // 1분마다 확인해도 처음 빈 시각은 그대로
        assertThat(room.isIdleFor(IDLE, T0.plusMinutes(31))).isTrue();
    }
}
