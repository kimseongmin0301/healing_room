package com.healingroom.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.healingroom.domain.admin.exception.AdminException;

class AdminLoginServiceTests {

    // scripts/admin_password.py 로 만든 테스트 전용 해시 (문장: "테스트용 문장 비밀번호 졸린 고양이"). 파이썬·자바 방식이 같은지 확인한다
    static final String PASSWORD = "테스트용 문장 비밀번호 졸린 고양이";
    static final String HASH = "pbkdf2_sha256$310000$tIrubXzuk009yoMTk841Mw==$QuY2v9IgyDO+E1+b2JmjhemR4rYBFFiHDQ0DLIk7ivk=";
    static final String IP = "203.0.113.10";

    MutableClock clock = new MutableClock(Instant.parse("2026-10-16T00:00:00Z"));
    AdminLoginService service = new AdminLoginService("owner", HASH, clock);

    @Test
    void verifiesHashMadeByPythonScript() {
        assertThat(AdminLoginService.verifyPassword(PASSWORD, HASH)).isTrue();
        assertThat(AdminLoginService.verifyPassword(PASSWORD + " ", HASH)).isFalse();
        assertThat(AdminLoginService.verifyPassword(PASSWORD, "plain-text")).isFalse();
    }

    @Test
    void loginGivesSessionThatExpiresAfterEightHours() {
        String session = service.login(IP, "owner", PASSWORD);
        assertThat(service.isLoggedIn(session)).isTrue();

        clock.now = clock.now.plus(AdminLoginService.SESSION_TTL);
        assertThat(service.isLoggedIn(session)).isFalse();
    }

    @Test
    void wrongUsernameOrPasswordIsRejected() {
        assertThatThrownBy(() -> service.login(IP, "owner", "틀린 문장")).isInstanceOf(AdminException.WrongLogin.class);
        assertThatThrownBy(() -> service.login(IP, "someone", PASSWORD)).isInstanceOf(AdminException.WrongLogin.class);
    }

    @Test
    void locksIpAfterFiveFailuresForFifteenMinutes() {
        for (int i = 0; i < AdminLoginService.MAX_FAILURES; i++) {
            assertThatThrownBy(() -> service.login(IP, "owner", "틀림")).isInstanceOf(AdminException.WrongLogin.class);
        }
        // 맞는 비밀번호여도 잠긴 동안은 막는다
        assertThatThrownBy(() -> service.login(IP, "owner", PASSWORD)).isInstanceOf(AdminException.LoginLocked.class);
        // 다른 IP 는 영향 없음
        assertThat(service.login("198.51.100.7", "owner", PASSWORD)).isNotBlank();

        clock.now = clock.now.plus(AdminLoginService.LOCK);
        assertThat(service.login(IP, "owner", PASSWORD)).isNotBlank();
    }

    @Test
    void logoutEndsSession() {
        String session = service.login(IP, "owner", PASSWORD);
        service.logout(session);
        assertThat(service.isLoggedIn(session)).isFalse();
        assertThatThrownBy(() -> service.require(session)).isInstanceOf(AdminException.LoginRequired.class);
    }

    @Test
    void withoutSettingsAdminIsDisabled() {
        AdminLoginService disabled = new AdminLoginService("", "", clock);
        assertThat(disabled.enabled()).isFalse();
        assertThatThrownBy(() -> disabled.login(IP, "owner", PASSWORD)).isInstanceOf(AdminException.Disabled.class);
        assertThatThrownBy(() -> disabled.require("x")).isInstanceOf(AdminException.Disabled.class);
    }

    static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
