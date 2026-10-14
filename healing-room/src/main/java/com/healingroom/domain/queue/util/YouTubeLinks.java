package com.healingroom.domain.queue.util;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자가 붙여넣은 YouTube 링크에서 영상 id(11자리)를 꺼낸다.
 * 지원: watch?v=, youtu.be/, shorts/, live/, embed/, 그리고 id 만 입력한 경우.
 */
public final class YouTubeLinks {

    private static final String ID = "([A-Za-z0-9_-]{11})";
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("^" + ID + "$"),
            Pattern.compile("(?:youtube\\.com|youtube-nocookie\\.com)/(?:watch\\?(?:.*&)?v=|shorts/|live/|embed/)" + ID),
            Pattern.compile("youtu\\.be/" + ID)
    );

    private YouTubeLinks() {
    }

    public static Optional<String> videoId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String text = input.trim();
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
