package com.healingroom.domain.chat.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.healingroom.domain.chat.dto.ChatMessageResponse;
import com.healingroom.domain.chat.dto.ChatPush;
import com.healingroom.domain.chat.entity.ChatRecord;
import com.healingroom.domain.chat.exception.ChatException;
import com.healingroom.domain.chat.repository.ChatRepository;
import com.healingroom.domain.presence.service.RoomBroadcaster;
import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.room.event.RoomClosedEvent;
import com.healingroom.domain.room.service.RoomService;

/**
 * 방 채팅.
 *
 * <ul>
 *   <li>방에 접속해 있는 사람만 보낼 수 있고, 도배를 막기 위해 짧은 시간에 보낼 수 있는 수를 제한한다.</li>
 *   <li>방마다 최근 history-size 개를 Redis 에 보관한다. 늦게 들어온 사람도 지난 대화를 볼 수 있다.</li>
 *   <li>보관 기간: 기본 방은 최근 default-room-retention-hours 시간, 사용자 방은 방이 닫힐 때까지.</li>
 *   <li>새 메시지는 Redis pub/sub 으로 알리고, 받은 서버가 자기에게 접속한 사람들에게 보낸다
 *       (서버가 여러 대여도 같은 방이면 모두 받는다).</li>
 * </ul>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    /** 사용자 방은 닫힐 때 지우지만, 혹시 놓쳐도 이 기간이 지나면 저절로 사라지게 한다 */
    private static final Duration USER_ROOM_SAFETY_TTL = Duration.ofDays(7);

    private final ChatRepository repository;
    private final RoomSessionRegistry sessions;
    private final RoomBroadcaster broadcaster;
    private final RoomService roomService;
    private final int historySize;
    private final Duration defaultRoomRetention;
    private final int rateLimit;
    private final Duration rateWindow;

    public ChatService(ChatRepository repository, RoomSessionRegistry sessions, RoomBroadcaster broadcaster,
                       RoomService roomService,
                       @Value("${healing-room.chat.history-size}") int historySize,
                       @Value("${healing-room.chat.default-room-retention-hours}") long retentionHours,
                       @Value("${healing-room.chat.rate-limit}") int rateLimit,
                       @Value("${healing-room.chat.rate-window-seconds}") long rateWindowSeconds) {
        this.repository = repository;
        this.sessions = sessions;
        this.broadcaster = broadcaster;
        this.roomService = roomService;
        this.historySize = historySize;
        this.defaultRoomRetention = Duration.ofHours(retentionHours);
        this.rateLimit = rateLimit;
        this.rateWindow = Duration.ofSeconds(rateWindowSeconds);
    }

    /** 메시지 보내기 */
    public ChatMessageResponse send(String roomId, String visitorId, String nickname, String text) {
        if (visitorId == null || !sessions.isPresent(roomId, visitorId)) {
            throw new ChatException.NotInRoom();
        }
        String cleanText = ChatPolicy.cleanText(text)
                .orElseThrow(() -> new ChatException.InvalidText(ChatPolicy.TEXT_MAX_LENGTH));
        String cleanNickname = ChatPolicy.cleanNickname(nickname)
                .orElseThrow(() -> new ChatException.InvalidNickname(ChatPolicy.NICKNAME_MAX_LENGTH));

        ChatRecord record = new ChatRecord(UUID.randomUUID().toString(), roomId, visitorId, cleanNickname,
                cleanText, System.currentTimeMillis());
        try {
            if (!repository.tryAcquire(visitorId, rateLimit, rateWindow)) {
                throw new ChatException.TooFast();
            }
            boolean defaultRoom = roomService.isDefault(roomId);
            repository.append(record, historySize, defaultRoom ? defaultRoomRetention : USER_ROOM_SAFETY_TTL);
            repository.publish(record);
        } catch (DataAccessException e) {
            log.warn("채팅 저장 실패 (room={}): {}", roomId, e.getMostSpecificCause().getMessage());
            throw new ChatException.Unavailable();
        }
        return ChatMessageResponse.of(record, visitorId);
    }

    /** 방의 지난 대화 (오래된 것부터). 기본 방은 보관 기간 안의 메시지만 */
    public List<ChatMessageResponse> history(String roomId, String visitorId) {
        try {
            long since = roomService.isDefault(roomId)
                    ? System.currentTimeMillis() - defaultRoomRetention.toMillis() : 0;
            return repository.recent(roomId).stream()
                    .filter(record -> record.sentAt() >= since)
                    .map(record -> ChatMessageResponse.of(record, visitorId))
                    .toList();
        } catch (DataAccessException e) {
            log.warn("채팅 기록 조회 실패 (room={}): {}", roomId, e.getMostSpecificCause().getMessage());
            throw new ChatException.Unavailable();
        }
    }

    /** Redis pub/sub 으로 받은 새 메시지를 이 서버에 접속한 방 사람들에게 보낸다 */
    public void onPublished(String payload) {
        ChatRecord record = repository.parse(payload);
        broadcaster.send(record.roomId(), visitorId -> ChatPush.of(ChatMessageResponse.of(record, visitorId)));
    }

    /** 방이 닫히면 그 방의 대화를 지운다 (DB 커밋이 끝난 뒤) */
    @TransactionalEventListener(fallbackExecution = true)
    public void onRoomClosed(RoomClosedEvent event) {
        try {
            repository.deleteRoom(event.roomId());
        } catch (DataAccessException e) {
            log.warn("닫힌 방 채팅 삭제 실패 (room={}), {}일 뒤 저절로 사라짐: {}", event.roomId(),
                    USER_ROOM_SAFETY_TTL.toDays(), e.getMostSpecificCause().getMessage());
        }
    }
}
