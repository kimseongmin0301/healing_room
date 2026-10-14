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
