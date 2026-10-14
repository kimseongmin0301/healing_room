package com.healingroom.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VotePolicyTests {

    @ParameterizedTest(name = "{0}명이면 {1}표 필요")
    @CsvSource({"0,1", "1,1", "2,2", "3,2", "4,3", "5,3", "10,6"})
    void requiredVotesIsMoreThanHalf(int listeners, int required) {
        assertThat(VotePolicy.required(listeners)).isEqualTo(required);
        assertThat(VotePolicy.passed(required, listeners)).isTrue();
        assertThat(VotePolicy.passed(required - 1, listeners)).isFalse();
    }
}
