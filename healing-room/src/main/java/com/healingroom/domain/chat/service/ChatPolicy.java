package com.healingroom.domain.chat.service;

import java.util.Optional;

/**
 * 채팅 입력 규칙: 앞뒤 공백 제거, 제어 문자 제거, 길이 제한.
 * 화면에서는 textContent 로만 넣으므로 HTML 은 따로 바꾸지 않는다.
 */
public final class ChatPolicy {

    public static final int TEXT_MAX_LENGTH = 300;
    public static final int NICKNAME_MAX_LENGTH = 12;

    private ChatPolicy() {
    }

    /** 보낼 수 있는 메시지면 정리된 문장, 아니면 비어 있음 */
    public static Optional<String> cleanText(String text) {
        return clean(text, TEXT_MAX_LENGTH);
    }

    public static Optional<String> cleanNickname(String nickname) {
        return clean(nickname, NICKNAME_MAX_LENGTH);
    }

    private static Optional<String> clean(String value, int maxLength) {
        if (value == null) {
            return Optional.empty();
        }
        // 줄바꿈·탭 같은 제어 문자는 공백으로, 연속 공백은 하나로
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s{2,}", " ").strip();
        int length = cleaned.codePointCount(0, cleaned.length()); // 이모지도 한 글자로 센다
        return cleaned.isEmpty() || length > maxLength ? Optional.empty() : Optional.of(cleaned);
    }
}
