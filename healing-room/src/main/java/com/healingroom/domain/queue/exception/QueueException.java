package com.healingroom.domain.queue.exception;

import org.springframework.http.HttpStatus;

/** 음악 대기열 기능에서 사용자에게 보여줄 오류. message 는 화면에 그대로 표시된다. */
public abstract class QueueException extends RuntimeException {

    private final HttpStatus status;

    protected QueueException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static class InvalidLink extends QueueException {
        public InvalidLink() {
            super(HttpStatus.BAD_REQUEST, "YouTube 영상 링크를 확인해 주세요.");
        }
    }

    public static class VideoUnavailable extends QueueException {
        public VideoUnavailable() {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "재생할 수 없는 영상이에요. 없는 영상이거나 외부 재생이 막혀 있어요.");
        }
    }

    public static class YouTubeUnreachable extends QueueException {
        public YouTubeUnreachable() {
            super(HttpStatus.BAD_GATEWAY, "YouTube 에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    public static class NotInRoom extends QueueException {
        public NotInRoom() {
            super(HttpStatus.FORBIDDEN, "방에 들어와 있어야 할 수 있어요.");
        }
    }

    public static class ItemNotFound extends QueueException {
        public ItemNotFound() {
            super(HttpStatus.NOT_FOUND, "이미 끝났거나 빠진 곡이에요.");
        }
    }

    public static class QueueFull extends QueueException {
        public QueueFull() {
            super(HttpStatus.CONFLICT, "대기열이 가득 찼어요. 조금 뒤에 다시 등록해 주세요.");
        }
    }
}
