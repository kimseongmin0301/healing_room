package com.healingroom.domain.queue.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 영상 제목에서 "곡 이름"만 뽑아, 같은 노래의 다른 영상(공식 MV, 라이브, 가사 영상 ...)을 알아본다.
 *
 * <p>예: "The Weeknd &amp; JENNIE - One Of The Girls (Live in Seoul)" 와
 * "The Weeknd, JENNIE, Lily-Rose Depp - One Of The Girls (Official Music Video)" 는 둘 다 "one of the girls".
 */
public final class SongTitles {

    private static final Pattern BRACKETS = Pattern.compile("[\\[(【「『].*?[\\])】」』]");
    private static final Pattern CHANNEL_SUFFIX = Pattern.compile("\\s[|·•].*$");
    // 영상 종류를 나타내는 말 (곡 이름이 아님)
    private static final Pattern EXTRA_WORDS = Pattern.compile(
            "\\b(official|music|video|mv|m/v|lyrics?|audio|live|ver|version|visuali[sz]er|performance|"
                    + "hd|4k|remaster(ed)?|teaser|full|eng|sub|kor|가사|공식|라이브|뮤직비디오)\\b");
    private static final Pattern FEATURING = Pattern.compile("\\s(feat\\.?|ft\\.?|featuring|with)\\s.*$");

    private SongTitles() {
    }

    /** 비교용 곡 이름 (소문자, 군더더기 없음). 알아낼 수 없으면 빈 문자열 */
    public static String songKey(String title) {
        if (title == null) {
            return "";
        }
        String t = BRACKETS.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ");
        t = CHANNEL_SUFFIX.matcher(t).replaceAll(" ");
        int dash = t.indexOf(" - ");
        if (dash >= 0 && dash + 3 < t.length()) {
            t = t.substring(dash + 3); // "가수 - 곡" 이면 곡 부분만
        }
        t = FEATURING.matcher(t).replaceAll(" ");
        t = EXTRA_WORDS.matcher(t).replaceAll(" ");
        return t.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    /** "가수 - 곡" 형태면 가수 부분, 아니면 빈 문자열 */
    public static String artist(String title) {
        if (title == null) {
            return "";
        }
        String t = BRACKETS.matcher(title).replaceAll(" ");
        int dash = t.indexOf(" - ");
        return dash > 0 ? t.substring(0, dash).strip() : "";
    }

    /**
     * 같은 노래로 보이면 true. 곡 이름이 같거나, 두 단어 이상인 곡 이름이 다른 쪽에 통째로 들어 있으면 같다고 본다
     * ("one of the girls" ⊂ "one of the girls in seoul"). 한 단어는 흔한 말("relaxing")일 수 있어 통째로 같을 때만.
     */
    public static boolean sameSong(String a, String b) {
        String ka = songKey(a), kb = songKey(b);
        if (ka.isEmpty() || kb.isEmpty()) {
            return false;
        }
        if (ka.equals(kb)) {
            return true;
        }
        String shorter = ka.length() <= kb.length() ? ka : kb;
        String longer = shorter == ka ? kb : ka;
        return shorter.contains(" ") && (" " + longer + " ").contains(" " + shorter + " ");
    }
}
