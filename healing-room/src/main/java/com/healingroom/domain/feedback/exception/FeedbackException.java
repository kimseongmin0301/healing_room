package com.healingroom.domain.feedback.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 의견 보내기에서 사용자에게 보여줄 오류 */
public abstract class FeedbackException extends ApiException {

    protected FeedbackException(HttpStatus status, String message) {
        super(status, message);
    }

    public static class InvalidMessage extends FeedbackException {
        public InvalidMessage(int maxLength) {
            super(HttpStatus.BAD_REQUEST, "의견은 1~" + maxLength + "자로 적어 주세요.");
        }
    }

    public static class MissingVisitor extends FeedbackException {
        public MissingVisitor() {
            super(HttpStatus.BAD_REQUEST, "페이지를 새로고침한 뒤 다시 보내 주세요.");
        }
    }

    public static class TooMany extends FeedbackException {
        public TooMany() {
            super(HttpStatus.TOO_MANY_REQUESTS, "의견을 여러 번 보냈어요. 잠시 후 다시 보내 주세요.");
        }
    }

    public static class NotFound extends FeedbackException {
        public NotFound() {
            super(HttpStatus.NOT_FOUND, "이미 지운 의견이에요.");
        }
    }
}
