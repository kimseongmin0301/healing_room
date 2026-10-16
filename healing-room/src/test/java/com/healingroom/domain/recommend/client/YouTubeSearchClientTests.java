package com.healingroom.domain.recommend.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YouTubeSearchClientTests {

    @Test
    void searchQueryDropsBracketsAndChannelSuffix() {
        assertThat(YouTubeSearchClient.searchQuery("jazz lofi mix [3 hours] 🎷 relaxing music | Lofi Girl"))
                .isEqualTo("jazz lofi mix relaxing music");
        assertThat(YouTubeSearchClient.searchQuery("[Playlist] 따뜻한 겨울 재즈 (Cozy Jazz) · 카페"))
                .isEqualTo("따뜻한 겨울 재즈");
    }

    @Test
    void searchQueryUsesArtistWhenTitleIsArtistDashSong() {
        // 곡 제목으로 찾으면 같은 노래의 다른 영상만 나오므로 가수 이름으로 찾는다
        assertThat(YouTubeSearchClient.searchQuery("The Weeknd & JENNIE - One Of The Girls (Live in Seoul)"))
                .isEqualTo("The Weeknd & JENNIE");
        assertThat(YouTubeSearchClient.searchQuery("[MV] 아이유(IU) - 밤편지")).isEqualTo("아이유");
    }

    @Test
    void recognizesBothQuotaErrors() {
        // 하루 할당량 초과(403)와 하루 검색 횟수 초과(429) 둘 다 "다 썼다"로 본다
        assertThat(YouTubeSearchClient.isQuotaError(403, "{\"reason\":\"quotaExceeded\"}")).isTrue();
        assertThat(YouTubeSearchClient.isQuotaError(429, "Quota exceeded for quota metric 'Search Queries'")).isTrue();
        assertThat(YouTubeSearchClient.isQuotaError(403, "{\"reason\":\"forbidden\"}")).isFalse();
        assertThat(YouTubeSearchClient.isQuotaError(500, "quota")).isFalse();
    }

    @Test
    void parsesIsoDuration() {
        assertThat(YouTubeSearchClient.parseSeconds("PT4M14S")).isEqualTo(254);
        assertThat(YouTubeSearchClient.parseSeconds("PT1H2M3S")).isEqualTo(3723);
        assertThat(YouTubeSearchClient.parseSeconds("P0D")).isZero(); // 라이브
        assertThat(YouTubeSearchClient.parseSeconds("")).isNull();
    }

    @Test
    void unescapesHtmlEntities() {
        assertThat(YouTubeSearchClient.unescape("Rock &amp; Roll &#39;Live&#39;")).isEqualTo("Rock & Roll 'Live'");
    }

    @Test
    void withoutApiKeyItReturnsNothingAndDoesNotCallYouTube() {
        YouTubeSearchClient client = new YouTubeSearchClient("", 12);
        assertThat(client.isAvailable()).isFalse();
        assertThat(client.similarTo("dQw4w9WgXcQ", "제목")).isEmpty();
    }
}
