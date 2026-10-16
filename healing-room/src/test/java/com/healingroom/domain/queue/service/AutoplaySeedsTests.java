package com.healingroom.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.healingroom.domain.queue.entity.QueueItem;
import com.healingroom.domain.queue.entity.QueueSource;

class AutoplaySeedsTests {

    static QueueItem request(String id) {
        return new QueueItem("sea", id, "신청곡 " + id, "visitor", 1);
    }

    static QueueItem system(String id, QueueSource source) {
        return QueueItem.systemTrack("sea", id, "자동 " + id, 1, source);
    }

    @Test
    void defaultTracksAreNeverSeeds() {
        // 기본 음악을 넘겨도, 그 전에 듣던 신청곡·자동 추천 곡을 기준으로 비슷한 곡을 찾는다
        QueueItem skippedDefault = system("d1", QueueSource.DEFAULT);
        List<QueueItem> recent = List.of(system("d2", QueueSource.DEFAULT), system("a1", QueueSource.AUTOPLAY),
                system("d3", QueueSource.DEFAULT), request("r1"));

        assertThat(QueueService.autoplaySeeds(skippedDefault, recent))
                .extracting(QueueItem::getYoutubeId).containsExactly("a1", "r1");
    }

    @Test
    void previousComesFirstAndDuplicatesAreDropped() {
        QueueItem previous = request("r1");
        List<QueueItem> recent = List.of(request("r1"), request("r2"), request("r2"), request("r3"),
                request("r4"), request("r5"), request("r6"));

        assertThat(QueueService.autoplaySeeds(previous, recent))
                .extracting(QueueItem::getYoutubeId).containsExactly("r1", "r2", "r3", "r4", "r5");
    }

    @Test
    void onlyDefaultTracksMeansNoSeeds() {
        assertThat(QueueService.autoplaySeeds(system("d1", QueueSource.DEFAULT),
                List.of(system("d2", QueueSource.DEFAULT)))).isEmpty();
    }
}
