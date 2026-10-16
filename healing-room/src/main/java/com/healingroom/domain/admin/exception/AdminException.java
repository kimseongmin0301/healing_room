package com.healingroom.domain.admin.exception;

import org.springframework.http.HttpStatus;

import com.healingroom.global.exception.ApiException;

/** 관리자 기능 오류 */
public abstract class AdminException extends ApiException {

    protected AdminException(HttpStatus status, String message) {
        super(status, message);
    }

    /** ADMIN_TOKEN 이 설정돼 있지 않으면 관리자 기능 자체가 없는 것처럼 보이게 한다 */
    public static class Disabled extends AdminException {
        public Disabled() {
            super(HttpStatus.NOT_FOUND, "없는 주소예요.");
        }
    }

    public static class Unauthorized extends AdminException {
        public Unauthorized() {
            super(HttpStatus.UNAUTHORIZED, "관리자 키가 맞지 않아요.");
        }
    }

    public static class LoginRequired extends AdminException {
        public LoginRequired() {
            super(HttpStatus.UNAUTHORIZED, "먼저 로그인해 주세요.");
        }
    }

    public static class WrongLogin extends AdminException {
        public WrongLogin() {
            super(HttpStatus.UNAUTHORIZED, "아이디나 비밀번호가 맞지 않아요.");
        }
    }

    public static class LoginLocked extends AdminException {
        public LoginLocked() {
            super(HttpStatus.TOO_MANY_REQUESTS, "여러 번 틀려서 잠시 막았어요. 15분 뒤 다시 시도해 주세요.");
        }
    }
}
