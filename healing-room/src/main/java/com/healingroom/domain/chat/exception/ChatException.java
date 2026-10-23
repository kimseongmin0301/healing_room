package com.healingroom.domain.chat.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 채팅 기능에서 사용자에게 보여줄 오류 */
public abstract class ChatException extends ApiException {

    protected ChatException(HttpStatus status, String message) {
        super(status, message);
    }

    public static class NotInRoom extends ChatException {
        public NotInRoom() {
            super(HttpStatus.FORBIDDEN, "방에 들어와 있어야 채팅할 수 있어요.");
        }
    }

    public static class InvalidText extends ChatException {
        public InvalidText(int maxLength) {
            super(HttpStatus.BAD_REQUEST, "메시지는 1~" + maxLength + "자로 보내 주세요.");
        }
    }

    public static class InvalidNickname extends ChatException {
        public InvalidNickname(int maxLength) {
            super(HttpStatus.BAD_REQUEST, "닉네임은 1~" + maxLength + "자로 지어 주세요.");
        }
    }

    public static class InvalidImage extends ChatException {
        public InvalidImage() {
            super(HttpStatus.BAD_REQUEST, "이미지는 JPEG·PNG·WebP 로, 너무 크지 않게 보내 주세요.");
        }
    }

    public static class TooManyImages extends ChatException {
        public TooManyImages(int limit) {
            super(HttpStatus.TOO_MANY_REQUESTS, "이미지는 1분에 " + limit + "장까지 보낼 수 있어요.");
        }
    }

    public static class TooFast extends ChatException {
        public TooFast() {
            super(HttpStatus.TOO_MANY_REQUESTS, "조금 천천히 보내 주세요.");
        }
    }

    public static class Unavailable extends ChatException {
        public Unavailable() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "채팅 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }
}
