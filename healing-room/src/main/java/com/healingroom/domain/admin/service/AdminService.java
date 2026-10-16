package com.healingroom.domain.admin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.healingroom.domain.admin.dto.AdminPresenceResponse;
import com.healingroom.domain.admin.exception.AdminException;
import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.service.RoomService;

/**
 * 관리자 전용 조회. 요청 헤더의 키가 ADMIN_TOKEN 과 같을 때만 허용한다.
 * ADMIN_TOKEN 이 비어 있으면 관리자 기능은 꺼진다(404).
 */
@Service
public class AdminService {

    private final String token;
    private final RoomSessionRegistry sessions;
    private final RoomService roomService;

    public AdminService(@Value("${healing-room.admin.token:}") String token,
                        RoomSessionRegistry sessions, RoomService roomService) {
        this.token = token == null ? "" : token.strip();
        this.sessions = sessions;
        this.roomService = roomService;
    }

    public void authorize(String given) {
        if (token.isEmpty()) {
            throw new AdminException.Disabled();
        }
        // 글자를 하나씩 비교하면 걸린 시간으로 키를 추측할 수 있어 항상 같은 시간이 걸리는 비교를 쓴다
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (given == null ? "" : given).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new AdminException.Unauthorized();
        }
    }

    /** 지금 사람이 있는 방과 접속자 (사람 많은 방부터) */
    public AdminPresenceResponse presence() {
        Map<String, String> names = roomService.findOpen().stream()
                .collect(Collectors.toMap(RoomResponse::id, RoomResponse::name, (a, b) -> a));
        List<AdminPresenceResponse.Room> rooms = sessions.activeRooms().stream()
                .map(roomId -> {
                    String hostId = sessions.host(roomId).orElse(null);
                    List<AdminPresenceResponse.Person> people = sessions.participants(roomId).stream()
                            .map(p -> new AdminPresenceResponse.Person(sessions.keyOf(roomId, p.visitorId()), p.nickname(), p.ip(),
                                    p.visitorId().substring(0, Math.min(8, p.visitorId().length())),
                                    p.visitorId().equals(hostId), p.joinedAt(), p.tabs()))
                            .toList();
                    return new AdminPresenceResponse.Room(roomId, names.getOrDefault(roomId, "(닫힌 방)"),
                            people.size(), people);
                })
                .filter(room -> room.count() > 0)
                .sorted(Comparator.comparingInt(AdminPresenceResponse.Room::count).reversed())
                .toList();
        return new AdminPresenceResponse(rooms.stream().mapToInt(AdminPresenceResponse.Room::count).sum(), rooms);
    }
}
