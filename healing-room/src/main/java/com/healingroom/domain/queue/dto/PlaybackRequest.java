package com.healingroom.domain.queue.dto;

/**
 * 방장의 재생 조작.
 *
 * @param action  pause(멈춤) / resume(이어서 재생) / seek(위치 이동)
 * @param seconds seek 일 때 옮길 위치(초)
 */
public record PlaybackRequest(String action, Double seconds) {
}
