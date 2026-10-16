package com.healingroom.domain.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.recommend.client.YouTubeSearchClient;
import com.healingroom.domain.recommend.dto.SearchResult;
import com.healingroom.domain.recommend.exception.SearchException;

class SearchServiceTests {

    static final String ROOM = "sea";
    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";
    static final List<SearchResult> RESULTS = List.of(new SearchResult("abc", "아이유 - 밤편지", "IU", "t.jpg", 254));

    YouTubeSearchClient youtube = mock(YouTubeSearchClient.class);
    RoomSessionRegistry sessions = mock(RoomSessionRegistry.class);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-16T00:00:00Z"));
    SearchService service = new SearchService(youtube, sessions, 3, clock);

    @BeforeEach
    void setUp() {
        given(sessions.isPresent(ROOM, VISITOR)).willReturn(true);
        given(youtube.isAvailable()).willReturn(true);
        given(youtube.search("밤편지")).willReturn(RESULTS);
    }

    @Test
    void searchesWhenInRoom() {
        assertThat(service.search(ROOM, VISITOR, "  밤편지 ")).isEqualTo(RESULTS);
    }

    @Test
    void onlyPeopleInTheRoomCanSearch() {
        assertThatThrownBy(() -> service.search(ROOM, "someone-else", "밤편지")).isInstanceOf(SearchException.NotInRoom.class);
        assertThatThrownBy(() -> service.search(ROOM, null, "밤편지")).isInstanceOf(SearchException.NotInRoom.class);
        verify(youtube, never()).search("밤편지");
    }

    @Test
    void rejectsEmptyOrTooLongQuery() {
        assertThatThrownBy(() -> service.search(ROOM, VISITOR, "  ")).isInstanceOf(SearchException.InvalidQuery.class);
        assertThatThrownBy(() -> service.search(ROOM, VISITOR, "가".repeat(SearchService.MAX_QUERY + 1)))
                .isInstanceOf(SearchException.InvalidQuery.class);
    }

    @Test
    void limitsSearchesPerMinuteAndAllowsAgainLater() {
        for (int i = 0; i < 3; i++) {
            service.search(ROOM, VISITOR, "밤편지");
        }
        assertThatThrownBy(() -> service.search(ROOM, VISITOR, "밤편지"))
                .isInstanceOf(SearchException.TooMany.class)
                .hasMessage("검색은 1분에 3번까지 할 수 있어요. 잠시 후 다시 해 주세요.");

        clock.now = clock.now.plusSeconds(61);
        assertThat(service.search(ROOM, VISITOR, "밤편지")).isEqualTo(RESULTS);
    }

    @Test
    void withoutApiKeySearchIsUnavailable() {
        given(youtube.isAvailable()).willReturn(false);
        assertThatThrownBy(() -> service.search(ROOM, VISITOR, "밤편지")).isInstanceOf(SearchException.Unavailable.class);
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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
