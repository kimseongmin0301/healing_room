package com.healingroom.domain.recommend.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 직접 검색에서 사용자에게 보여줄 오류 */
public abstract class SearchException extends ApiException {

    protected SearchException(HttpStatus status, String message) {
        super(status, message);
    }

    public static class InvalidQuery extends SearchException {
        public InvalidQuery(int max) {
            super(HttpStatus.BAD_REQUEST, "검색어는 1~" + max + "자로 입력해 주세요.");
        }
    }

    public static class NotInRoom extends SearchException {
        public NotInRoom() {
            super(HttpStatus.FORBIDDEN, "방에 들어와 있어야 검색할 수 있어요.");
        }
    }

    public static class Unavailable extends SearchException {
        public Unavailable() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "지금은 검색을 쓸 수 없어요. YouTube 링크로 신청해 주세요.");
        }
    }

    public static class QuotaExhausted extends SearchException {
        public QuotaExhausted() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "오늘 검색 횟수를 다 썼어요. YouTube 링크로 신청해 주세요.");
        }
    }

    public static class TooMany extends SearchException {
        public TooMany(int perMinute) {
            super(HttpStatus.TOO_MANY_REQUESTS, "검색은 1분에 " + perMinute + "번까지 할 수 있어요. 잠시 후 다시 해 주세요.");
        }
    }
}
