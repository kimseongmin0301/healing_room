package com.healingroom.domain.chat.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.healingroom.domain.chat.entity.ChatRecord;

import tools.jackson.databind.json.JsonMapper;

/**
 * 채팅을 Redis 에 저장한다. Redis 는 다른 서비스와 같이 쓰므로 모든 키·채널 앞에 접두어(healing-room:)를 붙인다.
 *
 * <ul>
 *   <li>{prefix}chat:{roomId}       List. 방의 최근 메시지(JSON), 오래된 것부터</li>
 *   <li>{prefix}chat-rate:{visitor} 도배 방지 카운터 (잠깐 살다 사라짐)</li>
 *   <li>{prefix}chat                pub/sub 채널. 새 메시지를 모든 서버에 알린다</li>
 * </ul>
 */
@Repository
public class ChatRepository {

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final String prefix;

    public ChatRepository(StringRedisTemplate redis, JsonMapper json,
                          @Value("${healing-room.redis.key-prefix}") String prefix) {
        this.redis = redis;
        this.json = json;
        this.prefix = prefix;
    }

    public String channel() {
        return prefix + "chat";
    }

    /** 메시지를 맨 뒤에 붙이고, 최근 keep 개만 남기고, ttl 동안 아무 말이 없으면 통째로 사라지게 한다 */
    public void append(ChatRecord record, int keep, Duration ttl) {
        String key = key(record.roomId());
        redis.opsForList().rightPush(key, json.writeValueAsString(record));
        redis.opsForList().trim(key, -keep, -1);
        redis.expire(key, ttl);
    }

    /** 방의 최근 메시지 (오래된 것부터) */
    public List<ChatRecord> recent(String roomId) {
        List<String> values = redis.opsForList().range(key(roomId), 0, -1);
        return values == null ? List.of() : values.stream().map(this::parse).toList();
    }

    public void deleteRoom(String roomId) {
        redis.delete(key(roomId));
    }

    /** 모든 방의 채팅을 지운다. KEYS 대신 SCAN 으로 이 앱의 채팅 키만 찾는다 (도배 방지 카운터는 남김) */
    public int deleteAllRooms() {
        int deleted = 0;
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(prefix + "chat:*").count(200).build())) {
            while (keys.hasNext()) {
                redis.delete(keys.next());
                deleted++;
            }
        }
        return deleted;
    }

    /** 채팅이 지워졌음을 모든 서버에 알린다. roomId 가 "*" 이면 모든 방 */
    public void publishClear(String roomId) {
        redis.convertAndSend(channel(), json.writeValueAsString(Map.of("type", "clear", "roomId", roomId)));
    }

    /** pub/sub 메시지가 "채팅 지움" 알림이면 대상 방 id, 새 메시지면 비어 있음 */
    public Optional<String> clearTarget(String payload) {
        var node = json.readTree(payload);
        return "clear".equals(node.path("type").asString(""))
                ? Optional.of(node.path("roomId").asString(""))
                : Optional.empty();
    }

    /** window 동안 limit 번까지 허용. 넘으면 false */
    public boolean tryAcquire(String visitorId, int limit, Duration window) {
        String key = prefix + "chat-rate:" + visitorId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        return count != null && count <= limit;
    }

    public void publish(ChatRecord record) {
        redis.convertAndSend(channel(), json.writeValueAsString(record));
    }

    public ChatRecord parse(String value) {
        return json.readValue(value, ChatRecord.class);
    }

    private String key(String roomId) {
        return prefix + "chat:" + roomId;
    }
}
