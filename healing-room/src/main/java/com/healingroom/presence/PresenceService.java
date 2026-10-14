package com.healingroom.presence;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 방마다 "지금 함께 있는 사람 수"를 Redis 로 센다.
 *
 * <p>키: {prefix}presence:{roomId} (Sorted Set) / 멤버: 방문자 id / 점수: 마지막 신호 시각(ms).
 * 브라우저가 주기적으로 신호(heartbeat)를 보내고, timeout 동안 신호가 없는 방문자는 지운다.
 */
@Service
public class PresenceService {

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Duration timeout;

    public PresenceService(StringRedisTemplate redis,
                           @Value("${healing-room.redis.key-prefix}") String keyPrefix,
                           @Value("${healing-room.presence.timeout-seconds}") long timeoutSeconds) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** 방문자가 방에 있다고 알리고, 현재 인원을 돌려준다. */
    public long heartbeat(String roomId, String visitorId) {
        String key = key(roomId);
        long now = System.currentTimeMillis();
        redis.opsForZSet().add(key, visitorId, now);
        // 아무도 신호를 보내지 않으면 키 자체가 사라지도록 만료 시간을 걸어 둔다
        redis.expire(key, timeout.multipliedBy(2));
        return count(key, now);
    }

    /** 방문자가 방을 떠났음을 알린다. */
    public void leave(String roomId, String visitorId) {
        redis.opsForZSet().remove(key(roomId), visitorId);
    }

    private long count(String key, long now) {
        redis.opsForZSet().removeRangeByScore(key, 0, now - timeout.toMillis());
        Long size = redis.opsForZSet().zCard(key);
        return size == null ? 0 : size;
    }

    private String key(String roomId) {
        return keyPrefix + "presence:" + roomId;
    }
}
