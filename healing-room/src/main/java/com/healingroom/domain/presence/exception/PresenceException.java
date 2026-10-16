package com.healingroom.domain.presence.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 접속자(내보내기) 기능에서 사용자에게 보여줄 오류 */
public abstract class PresenceException extends ApiException {

    protected PresenceException(HttpStatus status, String message) {
        super(status, message);
    }

    public static class HostOnly extends PresenceException {
        public HostOnly() {
            super(HttpStatus.FORBIDDEN, "방장만 내보낼 수 있어요.");
        }
    }

    public static class NotHere extends PresenceException {
        public NotHere() {
            super(HttpStatus.NOT_FOUND, "이미 방을 나간 사람이에요.");
        }
    }

    public static class NotYourself extends PresenceException {
        public NotYourself() {
            super(HttpStatus.BAD_REQUEST, "자기 자신은 내보낼 수 없어요.");
        }
    }
}
