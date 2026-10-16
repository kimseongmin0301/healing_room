package com.healingroom.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.healingroom.domain.feedback.entity.Feedback;
import com.healingroom.domain.feedback.exception.FeedbackException;
import com.healingroom.domain.feedback.repository.FeedbackRepository;

class FeedbackServiceTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";

    FeedbackRepository repository = mock(FeedbackRepository.class);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-16T00:00:00Z"));
    FeedbackService service = new FeedbackService(repository, clock);

    @Test
    void savesCleanedMessage() {
        service.send(VISITOR, "  숲 테마 좋아요\u0007\n\n\n\n다음엔 밤하늘도!  ");

        ArgumentCaptor<Feedback> saved = ArgumentCaptor.forClass(Feedback.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).isEqualTo("숲 테마 좋아요\n\n다음엔 밤하늘도!");
        assertThat(saved.getValue().getVisitorId()).isEqualTo(VISITOR);
    }

    @Test
    void rejectsEmptyOrTooLong() {
        assertThatThrownBy(() -> service.send(VISITOR, "   ")).isInstanceOf(FeedbackException.InvalidMessage.class);
        assertThatThrownBy(() -> service.send(VISITOR, "가".repeat(FeedbackService.MAX_LENGTH + 1)))
                .isInstanceOf(FeedbackException.InvalidMessage.class);
        assertThatThrownBy(() -> service.send(null, "안녕")).isInstanceOf(FeedbackException.MissingVisitor.class);
        verify(repository, never()).save(any());
    }

    @Test
    void limitsPerVisitorAndAllowsAgainLater() {
        for (int i = 0; i < FeedbackService.PER_VISITOR; i++) {
            service.send(VISITOR, "의견 " + i);
        }
        assertThatThrownBy(() -> service.send(VISITOR, "또")).isInstanceOf(FeedbackException.TooMany.class);

        clock.now = clock.now.plusSeconds(11 * 60);
        service.send(VISITOR, "10분 지나서");
        verify(repository, times(FeedbackService.PER_VISITOR + 1)).save(any());
    }

    @Test
    void limitsTotalPerHourEvenWithDifferentVisitors() {
        for (int i = 0; i < FeedbackService.PER_HOUR_TOTAL; i++) {
            service.send("visitor-" + i, "의견");
        }
        assertThatThrownBy(() -> service.send("visitor-new", "의견")).isInstanceOf(FeedbackException.TooMany.class);
    }

    static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
