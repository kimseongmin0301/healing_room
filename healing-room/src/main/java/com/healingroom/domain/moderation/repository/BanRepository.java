package com.healingroom.domain.moderation.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.healingroom.domain.moderation.entity.BanRecord;

import tools.jackson.databind.json.JsonMapper;

/**
 * 이용 제한을 Redis 에 둔다. 세 키 모두 제한 시간(TTL)이 지나면 Redis 가 스스로 지운다 (서버를 다시 켜도 유지됨).
 *
 * <ul>
 *   <li>{prefix}ban:{id}               제한 기록(JSON). 관리자 목록·풀기</li>
 *   <li>{prefix}ban-ip:{ip}            제한 id. IP 로 찾기</li>
 *   <li>{prefix}ban-visitor:{visitor}  제한 id. 방문자 번호로 찾기</li>
 * </ul>
 */
@Repository
public class BanRepository {

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final String prefix;

    public BanRepository(StringRedisTemplate redis, JsonMapper json,
                         @Value("${healing-room.redis.key-prefix}") String prefix) {
        this.redis = redis;
        this.json = json;
        this.prefix = prefix;
    }

    public void save(BanRecord ban, Duration ttl) {
        redis.opsForValue().set(banKey(ban.id()), json.writeValueAsString(ban), ttl);
        if (ban.ip() != null) {
            redis.opsForValue().set(ipKey(ban.ip()), ban.id(), ttl);
        }
        if (ban.visitorId() != null) {
            redis.opsForValue().set(visitorKey(ban.visitorId()), ban.id(), ttl);
        }
    }

    /** 이 방문자 번호나 IP 에 걸린 제한들 */
    public List<BanRecord> findFor(String visitorId, String ip) {
        List<String> keys = new ArrayList<>();
        if (visitorId != null) {
            keys.add(visitorKey(visitorId));
        }
        if (ip != null) {
            keys.add(ipKey(ip));
        }
        if (keys.isEmpty()) {
            return List.of();
        }
        List<String> ids = redis.opsForValue().multiGet(keys);
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().map(this::find).flatMap(Optional::stream).toList();
    }

    public Optional<BanRecord> find(String id) {
        String value = redis.opsForValue().get(banKey(id));
        return value == null ? Optional.empty() : Optional.of(json.readValue(value, BanRecord.class));
    }

    /** 지금 걸려 있는 제한 전부. KEYS 대신 SCAN 으로 이 앱의 제한 기록 키만 찾는다 */
    public List<BanRecord> findAll() {
        List<BanRecord> out = new ArrayList<>();
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(prefix + "ban:*").count(200).build())) {
            while (keys.hasNext()) {
                String value = redis.opsForValue().get(keys.next());
                if (value != null) {
                    out.add(json.readValue(value, BanRecord.class));
                }
            }
        }
        return out;
    }

    /** 제한 풀기. IP·방문자 키는 다른(더 나중의) 제한을 가리키고 있으면 남겨 둔다 */
    public void delete(BanRecord ban) {
        redis.delete(banKey(ban.id()));
        for (String key : new String[] {ban.ip() == null ? null : ipKey(ban.ip()),
                ban.visitorId() == null ? null : visitorKey(ban.visitorId())}) {
            if (key != null && ban.id().equals(redis.opsForValue().get(key))) {
                redis.delete(key);
            }
        }
    }

    private String banKey(String id) {
        return prefix + "ban:" + id;
    }

    private String ipKey(String ip) {
        return prefix + "ban-ip:" + ip;
    }

    private String visitorKey(String visitorId) {
        return prefix + "ban-visitor:" + visitorId;
    }
}
