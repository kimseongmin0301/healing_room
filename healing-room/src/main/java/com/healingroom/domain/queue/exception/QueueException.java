package com.healingroom.domain.queue.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 음악 대기열 기능에서 사용자에게 보여줄 오류 */
public abstract class QueueException extends ApiException {

    protected QueueException(HttpStatus status, String message) {
        super(status, message);
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

        protected VideoUnavailable(String message) {
            super(HttpStatus.UNPROCESSABLE_CONTENT, message);
        }
    }

    /** 업로더가 다른 사이트에서 재생하지 못하게 막아 둔 영상 (oEmbed 401·403) */
    public static class EmbedBlocked extends VideoUnavailable {
        public EmbedBlocked() {
            super("업로더가 다른 사이트에서 재생하지 못하게 막아 둔 영상이에요. 같은 노래의 다른 영상(가사·음원 영상 등)을 찾아 주세요.");
        }
    }

    /** 없거나 비공개인 영상 (oEmbed 400·404) */
    public static class VideoNotFound extends VideoUnavailable {
        public VideoNotFound() {
            super("없거나 비공개인 영상이에요. 링크를 다시 확인해 주세요.");
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

    public static class HostOnly extends QueueException {
        public HostOnly() {
            super(HttpStatus.FORBIDDEN, "방장만 할 수 있어요.");
        }
    }

    public static class NothingPlaying extends QueueException {
        public NothingPlaying() {
            super(HttpStatus.CONFLICT, "지금 재생 중인 곡이 없어요.");
        }
    }

    public static class QueueFull extends QueueException {
        public QueueFull() {
            super(HttpStatus.CONFLICT, "대기열이 가득 찼어요. 조금 뒤에 다시 등록해 주세요.");
        }
    }
}
