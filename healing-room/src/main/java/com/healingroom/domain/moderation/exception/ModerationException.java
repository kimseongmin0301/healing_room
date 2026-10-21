package com.healingroom.domain.moderation.exception;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 욕설·음란어 제한에서 사용자에게 보여줄 오류 */
public abstract class ModerationException extends ApiException {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("a h시 mm분", Locale.KOREAN)
            .withZone(ZoneId.of("Asia/Seoul"));

    protected ModerationException(HttpStatus status, String message) {
        super(status, message);
    }

    public static class Banned extends ModerationException {
        public Banned(long until) {
            super(HttpStatus.FORBIDDEN, "욕설이나 음란한 말을 써서 " + TIME.format(Instant.ofEpochMilli(until))
                    + "까지 채팅·방 만들기·방 입장이 제한됐어요.");
        }
    }

    public static class NotFound extends ModerationException {
        public NotFound() {
            super(HttpStatus.NOT_FOUND, "이미 풀렸거나 없는 제한이에요.");
        }
    }
}
