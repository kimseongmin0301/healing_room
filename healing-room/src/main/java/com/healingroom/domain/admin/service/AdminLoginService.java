package com.healingroom.domain.admin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.healingroom.domain.admin.exception.AdminException;

/**
 * 관리자 로그인 (관리자 키 앞 단계). 로그인해야 /admin 화면이 열리고, 그 뒤에도 관리자 키(ADMIN_TOKEN)를 넣어야 한다.
 *
 * <ul>
 *   <li>아이디 ADMIN_USERNAME, 비밀번호는 원문이 아니라 해시 ADMIN_PASSWORD_HASH 로만 둔다
 *       (형식 {@code pbkdf2_sha256$반복횟수$소금$해시}, scripts/admin_password.py 로 만든다)</li>
 *   <li>한 IP 가 {@value #MAX_FAILURES}번 틀리면 15분 막는다 (무작위 대입 방지)</li>
 *   <li>로그인은 8시간 유지. 로그인 표는 서버 메모리에만 두므로 서버를 다시 켜면 다시 로그인한다</li>
 *   <li>아이디·해시가 비어 있으면 관리자 기능 전체가 꺼진다 (없는 주소처럼 404)</li>
 * </ul>
 */
@Service
public class AdminLoginService {

    public static final String COOKIE = "HR_ADMIN";
    public static final Duration SESSION_TTL = Duration.ofHours(8);
    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(15);

    private record Failures(int count, Instant lockedUntil) {
    }

    private final String username;
    private final String passwordHash;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();   // 로그인 표 → 만료 시각
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();  // IP → 틀린 횟수

    @Autowired
    public AdminLoginService(@Value("${healing-room.admin.username:}") String username,
                             @Value("${healing-room.admin.password-hash:}") String passwordHash) {
        this(username, passwordHash, Clock.systemUTC());
    }

    AdminLoginService(String username, String passwordHash, Clock clock) {
        this.username = username == null ? "" : username.strip();
        this.passwordHash = passwordHash == null ? "" : passwordHash.strip();
        this.clock = clock;
    }

    public boolean enabled() {
        return !username.isEmpty() && !passwordHash.isEmpty();
    }

    /** 맞으면 로그인 표(쿠키 값)를 돌려준다 */
    public String login(String ip, String givenUsername, String givenPassword) {
        if (!enabled()) {
            throw new AdminException.Disabled();
        }
        Instant now = clock.instant();
        Failures failed = failures.get(ip);
        if (failed != null && failed.lockedUntil() != null && now.isBefore(failed.lockedUntil())) {
            throw new AdminException.LoginLocked();
        }
        // 아이디가 틀려도 비밀번호 계산은 똑같이 해서, 걸린 시간으로 아이디를 알아낼 수 없게 한다
        boolean passwordOk = verifyPassword(givenPassword == null ? "" : givenPassword, passwordHash);
        boolean usernameOk = MessageDigest.isEqual(username.getBytes(StandardCharsets.UTF_8),
                (givenUsername == null ? "" : givenUsername.strip()).getBytes(StandardCharsets.UTF_8));
        if (!(passwordOk && usernameOk)) {
            failures.compute(ip, (key, old) -> {
                int count = (old == null || (old.lockedUntil() != null && !now.isBefore(old.lockedUntil()))) ? 1 : old.count() + 1;
                return new Failures(count, count >= MAX_FAILURES ? now.plus(LOCK) : null);
            });
            throw new AdminException.WrongLogin();
        }
        failures.remove(ip);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String session = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(session, now.plus(SESSION_TTL));
        return session;
    }

    public boolean isLoggedIn(String session) {
        if (session == null || session.isBlank() || !enabled()) {
            return false;
        }
        Instant expires = sessions.get(session);
        if (expires == null) {
            return false;
        }
        if (!clock.instant().isBefore(expires)) {
            sessions.remove(session);
            return false;
        }
        return true;
    }

    /** 관리자 API 앞에서 부른다. 로그인 기능이 꺼져 있으면 404, 로그인 안 했으면 401 */
    public void require(String session) {
        if (!enabled()) {
            throw new AdminException.Disabled();
        }
        if (!isLoggedIn(session)) {
            throw new AdminException.LoginRequired();
        }
    }

    public void logout(String session) {
        if (session != null) {
            sessions.remove(session);
        }
    }

    /** 저장된 형식: pbkdf2_sha256$반복횟수$소금(Base64)$해시(Base64) */
    static boolean verifyPassword(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expected.length * 8);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /** 만료된 로그인·잠금 정리 */
    @Scheduled(fixedDelay = 600_000)
    void forgetOld() {
        Instant now = clock.instant();
        sessions.values().removeIf(expires -> !now.isBefore(expires));
        failures.values().removeIf(f -> f.lockedUntil() == null || !now.isBefore(f.lockedUntil()));
    }
}
