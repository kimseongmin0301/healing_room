package com.healingroom.domain.queue.dto;

/** 방장의 대기열 순서 바꾸기. direction: up(앞으로) / down(뒤로) */
public record MoveRequest(String direction) {
}
