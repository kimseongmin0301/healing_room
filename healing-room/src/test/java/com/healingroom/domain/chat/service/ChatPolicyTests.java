package com.healingroom.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatPolicyTests {

    @Test
    void trimsAndFlattensControlCharacters() {
        assertThat(ChatPolicy.cleanText("  안녕하세요\n\n반가워요\t ")).contains("안녕하세요 반가워요");
    }

    @Test
    void rejectsEmptyOrTooLongText() {
        assertThat(ChatPolicy.cleanText("   \n ")).isEmpty();
        assertThat(ChatPolicy.cleanText(null)).isEmpty();
        assertThat(ChatPolicy.cleanText("가".repeat(ChatPolicy.TEXT_MAX_LENGTH))).isPresent();
        assertThat(ChatPolicy.cleanText("가".repeat(ChatPolicy.TEXT_MAX_LENGTH + 1))).isEmpty();
    }

    @Test
    void emojiCountsAsOneCharacter() {
        assertThat(ChatPolicy.cleanNickname("🔥".repeat(ChatPolicy.NICKNAME_MAX_LENGTH))).isPresent();
    }

    @Test
    void nicknameLengthLimit() {
        assertThat(ChatPolicy.cleanNickname("졸린 고양이")).contains("졸린 고양이");
        assertThat(ChatPolicy.cleanNickname("가".repeat(ChatPolicy.NICKNAME_MAX_LENGTH + 1))).isEmpty();
        assertThat(ChatPolicy.cleanNickname(" ")).isEmpty();
    }

    @Test
    void htmlIsKeptAsPlainText() {
        // 화면에서 textContent 로만 넣으므로 그대로 둔다 (스크립트로 실행되지 않음)
        assertThat(ChatPolicy.cleanText("<b>hi</b>")).contains("<b>hi</b>");
    }
}
