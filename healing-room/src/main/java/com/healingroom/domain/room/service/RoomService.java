package com.healingroom.domain.room.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
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
import com.healingroom.domain.room.event.RoomUpdatedEvent;
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
    static final int MIN_PEOPLE = 2;   // 최대 인원으로 고를 수 있는 범위
    static final int MAX_PEOPLE = 50;
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

    /** 열려 있는 모든 방 + 방마다 지금 있는 사람 수 (관리자 등) */
    public List<RoomResponse> findOpen() {
        return openRooms(room -> true);
    }

    /**
     * 로비에 보일 방: 늘 보이는 방(featured: 불멍·바다멍), 사용자가 만든 방, 사람이 있는 기본 방.
     * 나머지 기본 방은 로비가 붐비지 않게 숨긴다 (주소 /{id} 로는 언제든 들어간다)
     */
    public List<RoomResponse> findLobby() {
        return openRooms(room -> room.isFeatured() || !room.isDefault() || sessions.listenerCount(room.getId()) > 0);
    }

    private List<RoomResponse> openRooms(Predicate<Room> show) {
        Map<String, ThemeResponse> themes = themeService.findAll().stream()
                .collect(Collectors.toMap(ThemeResponse::id, Function.identity()));
        return rooms.findByClosedAtIsNullOrderByIsDefaultDescCreatedAtAsc().stream()
                .filter(room -> themes.containsKey(room.getThemeId()))
                .filter(show)
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

    /** 방 만들기. 만든 사람이 들어오면 가장 먼저 들어온 사람이므로 방장이 된다. maxPeople 이 null 이면 인원 제한 없음 */
    @Transactional
    public RoomResponse create(String visitorId, String name, String themeId, Integer maxPeople) {
        if (!isUuid(visitorId)) {
            throw new RoomException.InvalidVisitor();
        }
        String trimmed = validName(name);
        ThemeResponse theme = validTheme(themeId);
        validMaxPeople(maxPeople);
        if (rooms.countByCreatedByAndClosedAtIsNull(visitorId) >= maxOpenPerVisitor) {
            throw new RoomException.TooManyRooms(maxOpenPerVisitor);
        }
        Room room = rooms.save(new Room(newId(), theme.id(), trimmed, visitorId, maxPeople, OffsetDateTime.now()));
        return RoomResponse.of(room, theme, 0);
    }

    public RoomResponse create(String visitorId, String name, String themeId) {
        return create(visitorId, name, themeId, null);
    }

    /** 방장이 방 설정(이름·테마·최대 인원)을 바꾼다. 기본 방은 바꿀 수 없다. 바뀌면 방 사람들 화면을 새로 그린다 */
    @Transactional
    public RoomResponse changeSettingsByHost(String roomId, String visitorId, String name, String themeId, Integer maxPeople) {
        return applySettings(hostRoom(roomId, visitorId), name, themeId, maxPeople);
    }

    /**
     * 관리자가 방 설정을 바꾼다. 사람이 만든 방은 모두 바꿀 수 있고,
     * 기본 방은 이름·최대 인원만 (테마는 주소 /{id}·테마 소개 페이지와 이어져 있어 고정)
     */
    @Transactional
    public RoomResponse changeSettingsByAdmin(String roomId, String name, String themeId, Integer maxPeople) {
        Room room = openRoom(roomId);
        if (room.isDefault() && !room.getThemeId().equals(themeId)) {
            throw new RoomException.DefaultRoomTheme();
        }
        return applySettings(room, name, themeId, maxPeople);
    }

    private RoomResponse applySettings(Room room, String name, String themeId, Integer maxPeople) {
        String trimmed = validName(name);
        ThemeResponse theme = validTheme(themeId);
        validMaxPeople(maxPeople);
        room.changeSettings(trimmed, theme.id(), maxPeople);
        events.publishEvent(new RoomUpdatedEvent(room.getId()));
        return RoomResponse.of(room, theme, sessions.listenerCount(room.getId()));
    }

    /** 방장이 방을 닫는다. 기본 방은 닫을 수 없다. 방에 있던 사람들은 로비로 간다 */
    @Transactional
    public void closeByHost(String roomId, String visitorId) {
        close(hostRoom(roomId, visitorId), RoomClosedEvent.HOST);
    }

    /** 관리자가 방을 없앤다(닫기). 기본 방은 테마마다 하나씩 늘 있어야 해서 없앨 수 없다 */
    @Transactional
    public void closeByAdmin(String roomId) {
        Room room = openRoom(roomId);
        if (room.isDefault()) {
            throw new RoomException.DefaultRoomLocked();
        }
        close(room, RoomClosedEvent.ADMIN);
    }

    private void close(Room room, String by) {
        room.close(OffsetDateTime.now());
        events.publishEvent(new RoomClosedEvent(room.getId(), by)); // 커밋된 뒤 채팅 정리 + 방 사람들에게 알림
    }

    /** 최대 인원 (제한 없으면 비어 있다) */
    public Optional<Integer> maxPeople(String roomId) {
        return rooms.findByIdAndClosedAtIsNull(roomId).map(Room::getMaxPeople);
    }

    private Room openRoom(String roomId) {
        return rooms.findByIdAndClosedAtIsNull(roomId).orElseThrow(RoomException.NotFound::new);
    }

    private Room hostRoom(String roomId, String visitorId) {
        Room room = openRoom(roomId);
        if (room.isDefault()) {
            throw new RoomException.DefaultRoomLocked();
        }
        if (visitorId == null || !sessions.isHost(roomId, visitorId)) {
            throw new RoomException.HostOnly();
        }
        return room;
    }

    private static String validName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw new RoomException.InvalidName(NAME_MAX_LENGTH);
        }
        return trimmed;
    }

    private ThemeResponse validTheme(String themeId) {
        return themeService.find(themeId == null ? "" : themeId).orElseThrow(RoomException.ThemeNotFound::new);
    }

    private static void validMaxPeople(Integer maxPeople) {
        if (maxPeople != null && (maxPeople < MIN_PEOPLE || maxPeople > MAX_PEOPLE)) {
            throw new RoomException.InvalidMaxPeople(MIN_PEOPLE, MAX_PEOPLE);
        }
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
