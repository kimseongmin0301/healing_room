package com.healingroom.domain.queue.service;

/**
 * 투표 규칙: 지금 방에 있는 사람의 50%를 넘게(과반) 찬성하면 통과.
 * (1명이면 1표, 2명이면 2표, 3명이면 2표, 4명이면 3표, 5명이면 3표 …)
 */
public final class VotePolicy {

    private VotePolicy() {
    }

    /** 통과에 필요한 표 수 */
    public static int required(int listeners) {
        return listeners / 2 + 1;
    }

    public static boolean passed(long votes, int listeners) {
        return votes >= required(listeners);
    }
}
