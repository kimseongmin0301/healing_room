package com.healingroom.domain.queue.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SongTitlesTests {

    @Test
    void songKeyKeepsOnlyTheSongName() {
        assertThat(SongTitles.songKey("The Weeknd, JENNIE, Lily-Rose Depp - One Of The Girls (Official Music Video)"))
                .isEqualTo("one of the girls");
        assertThat(SongTitles.songKey("NewJeans (뉴진스) 'Ditto' Official MV")).isEqualTo("newjeans ditto");
        assertThat(SongTitles.songKey("아이유 - 밤편지 [가사/Lyrics]")).isEqualTo("밤편지");
    }

    @Test
    void sameSongInDifferentVideosIsDetected() {
        String live = "The Weeknd & JENNIE - One Of The Girls (Live in Seoul, South Korea)";
        String official = "The Weeknd, JENNIE, Lily-Rose Depp - One Of The Girls (Official Music Video)";
        String lyrics = "One Of The Girls - The Weeknd, JENNIE (Lyrics)";
        assertThat(SongTitles.sameSong(live, official)).isTrue();
        assertThat(SongTitles.sameSong(official, "The Weeknd - One Of The Girls ft. JENNIE | Lyrics")).isTrue();
        // "곡 - 가수" 순서는 곡 이름을 정확히 못 뽑지만, 다른 곡으로 잘못 거르지는 않는다
        assertThat(SongTitles.sameSong(lyrics, "The Weeknd - Blinding Lights")).isFalse();
    }

    @Test
    void differentSongsAreNotTheSame() {
        assertThat(SongTitles.sameSong("The Weeknd - Blinding Lights (Official Video)",
                "The Weeknd - Save Your Tears (Official Music Video)")).isFalse();
        assertThat(SongTitles.sameSong("1 A.M Study Session · Lofi Girl", "12 A.M Study Session · Lofi Girl")).isFalse();
        // 한 단어짜리 흔한 말은 다른 제목에 들어 있어도 같은 곡으로 보지 않는다
        assertThat(SongTitles.sameSong("Relaxing Music", "Relaxing Piano for Sleep")).isFalse();
    }

    @Test
    void artistIsThePartBeforeTheDash() {
        assertThat(SongTitles.artist("The Weeknd & JENNIE - One Of The Girls")).isEqualTo("The Weeknd & JENNIE");
        assertThat(SongTitles.artist("jazz lofi mix [3 hours]")).isEmpty();
    }
}
