package com.healingroom.domain.room.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.room.dto.RoomDetailResponse;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.entity.Room;
import com.healingroom.domain.room.event.RoomClosedEvent;
import com.healingroom.domain.room.exception.RoomException;
import com.healingroom.domain.room.repository.RoomRepository;
import com.healingroom.domain.theme.dto.ThemeResponse;
import com.healingroom.domain.theme.service.ThemeService;

/**
 * 방 목록, 방 만들기, 빈 방 닫기.
 * 방에 아무도 없는 채로 idle-close-minutes 가 지나면 닫는다 (기본 방은 닫지 않음).
 */
@Service
@Transactional(readOnly = true)
public class RoomService {

    static final int NAME_MAX_LENGTH = 30;
    private static final String ID_CHARS = "abcdefghijkmnpqrstuvwxyz23456789"; // 헷갈리는 l, o, 0, 1 제외
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository rooms;
    private final ThemeService themeService;
    private final RoomSessionRegistry sessions;
    private final ApplicationEventPublisher events;
    private final Duration idleClose;
    private final int maxOpenPerVisitor;

    public RoomService(RoomRepository rooms, ThemeService themeService, RoomSessionRegistry sessions,
                       ApplicationEventPublisher events,
                       @Value("${healing-room.room.idle-close-minutes}") long idleCloseMinutes,
                       @Value("${healing-room.room.max-open-per-visitor}") int maxOpenPerVisitor) {
        this.rooms = rooms;
        this.themeService = themeService;
        this.sessions = sessions;
        this.events = events;
        this.idleClose = Duration.ofMinutes(idleCloseMinutes);
        this.maxOpenPerVisitor = maxOpenPerVisitor;
    }

    /** 열려 있는 방 목록 + 방마다 지금 있는 사람 수 (로비) */
    public List<RoomResponse> findOpen() {
        Map<String, ThemeResponse> themes = themeService.findAll().stream()
                .collect(Collectors.toMap(ThemeResponse::id, Function.identity()));
        return rooms.findByClosedAtIsNullOrderByIsDefaultDescCreatedAtAsc().stream()
                .filter(room -> themes.containsKey(room.getThemeId()))
                .map(room -> RoomResponse.of(room, themes.get(room.getThemeId()), sessions.listenerCount(room.getId())))
                .toList();
    }

    /** 방 하나 + 환경음 (방에 들어갈 때). 닫힌 방이면 비어 있다 */
    public Optional<RoomDetailResponse> findDetail(String roomId) {
        return rooms.findByIdAndClosedAtIsNull(roomId).flatMap(room -> themeService.find(room.getThemeId())
                .map(theme -> new RoomDetailResponse(
                        RoomResponse.of(room, theme, sessions.listenerCount(roomId)),
                        themeService.ambients(theme.id()))));
    }

    public boolean isOpen(String roomId) {
        return rooms.existsByIdAndClosedAtIsNull(roomId);
    }

    /** 항상 열려 있는 기본 방인지 */
    public boolean isDefault(String roomId) {
        return rooms.findById(roomId).map(Room::isDefault).orElse(false);
    }

    public Optional<String> themeIdOf(String roomId) {
        return rooms.findById(roomId).map(Room::getThemeId);
    }

    /** 방 만들기. 만든 사람이 들어오면 가장 먼저 들어온 사람이므로 방장이 된다 */
    @Transactional
    public RoomResponse create(String visitorId, String name, String themeId) {
        if (!isUuid(visitorId)) {
            throw new RoomException.InvalidVisitor();
        }
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new RoomException.InvalidName(NAME_MAX_LENGTH);
        }
        ThemeResponse theme = themeService.find(themeId == null ? "" : themeId)
                .orElseThrow(RoomException.ThemeNotFound::new);
        if (rooms.countByCreatedByAndClosedAtIsNull(visitorId) >= maxOpenPerVisitor) {
            throw new RoomException.TooManyRooms(maxOpenPerVisitor);
        }
        Room room = rooms.save(new Room(newId(), theme.id(), trimmed, visitorId, OffsetDateTime.now()));
        return RoomResponse.of(room, theme, 0);
    }

    /** 누가 들어오거나 나갔을 때: 빈 방이 된 시각을 기록하거나 지운다 */
    @Transactional
    public void onPresenceChanged(String roomId) {
        rooms.findById(roomId).ifPresent(room ->
                room.updateOccupancy(sessions.listenerCount(roomId) > 0, OffsetDateTime.now()));
    }

    /** 1분마다: 오래 비어 있던 방을 닫는다 */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeIdleRooms() {
        OffsetDateTime now = OffsetDateTime.now();
        for (Room room : rooms.findByClosedAtIsNullOrderByIsDefaultDescCreatedAtAsc()) {
            boolean occupied = sessions.listenerCount(room.getId()) > 0;
            room.updateOccupancy(occupied, now); // 서버 재시작 등으로 놓친 기록을 맞춘다
            if (!occupied && room.isIdleFor(idleClose, now)) {
                room.close(now);
                events.publishEvent(new RoomClosedEvent(room.getId())); // 커밋된 뒤 채팅 기록 등을 정리
            }
        }
    }

    private String newId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
            }
            id = sb.toString();
        } while (rooms.existsById(id));
        return id;
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
