package com.healingroom.domain.queue.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YouTubeLinksTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?list=PL123&v=dQw4w9WgXcQ&t=30s",
            "https://youtu.be/dQw4w9WgXcQ?si=xyz",
            "https://m.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "  https://www.youtube.com/embed/dQw4w9WgXcQ  ",
    })
    void extractsVideoId(String input) {
        assertThat(YouTubeLinks.videoId(input)).contains("dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "hello", "https://example.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/@LofiGirl"})
    void rejectsNonVideoLinks(String input) {
        assertThat(YouTubeLinks.videoId(input)).isEmpty();
    }
}
