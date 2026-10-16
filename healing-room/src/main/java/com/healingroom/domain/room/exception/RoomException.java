package com.healingroom.domain.room.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 방 기능에서 사용자에게 보여줄 오류 */
public abstract class RoomException extends ApiException {

    protected RoomException(HttpStatus status, String message) {
        super(status, message);
    }

    public static class InvalidName extends RoomException {
        public InvalidName(int maxLength) {
            super(HttpStatus.BAD_REQUEST, "방 이름은 1~" + maxLength + "자로 지어 주세요.");
        }
    }

    public static class ThemeNotFound extends RoomException {
        public ThemeNotFound() {
            super(HttpStatus.BAD_REQUEST, "테마를 골라 주세요.");
        }
    }

    public static class TooManyRooms extends RoomException {
        public TooManyRooms(int max) {
            super(HttpStatus.CONFLICT, "한 사람이 동시에 열 수 있는 방은 " + max + "개까지예요. 빈 방은 30분 뒤에 닫혀요.");
        }
    }

    public static class ResetNotAllowed extends RoomException {
        public ResetNotAllowed() {
            super(HttpStatus.FORBIDDEN, "기록 초기화는 기본 방에서 방장만 할 수 있어요.");
        }
    }

    public static class InvalidMaxPeople extends RoomException {
        public InvalidMaxPeople(int min, int max) {
            super(HttpStatus.BAD_REQUEST, "최대 인원은 " + min + "~" + max + "명으로 정해 주세요.");
        }
    }

    public static class HostOnly extends RoomException {
        public HostOnly() {
            super(HttpStatus.FORBIDDEN, "방장만 할 수 있어요.");
        }
    }

    public static class DefaultRoomLocked extends RoomException {
        public DefaultRoomLocked() {
            super(HttpStatus.FORBIDDEN, "기본 방은 설정을 바꾸거나 닫을 수 없어요.");
        }
    }

    public static class DefaultRoomTheme extends RoomException {
        public DefaultRoomTheme() {
            super(HttpStatus.FORBIDDEN, "기본 방의 테마는 바꿀 수 없어요. 주소와 테마 소개 페이지가 이어져 있어요.");
        }
    }

    public static class NotFound extends RoomException {
        public NotFound() {
            super(HttpStatus.NOT_FOUND, "닫혔거나 없는 방이에요.");
        }
    }

    public static class InvalidVisitor extends RoomException {
        public InvalidVisitor() {
            super(HttpStatus.BAD_REQUEST, "방문자 정보를 확인할 수 없어요. 새로고침해 주세요.");
        }
    }
}
