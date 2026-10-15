package com.healingroom.domain.presence.dto;

import java.util.List;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.presence.util.IpMask;

/**
 * WebSocket 으로 보내는 접속자 목록 (type=presence). 사람이 들어오거나 나가거나 닉네임을 바꾸면 방 전체에 보낸다.
 * 방문자 id 와 전체 IP 는 내보내지 않는다 (IP 는 앞부분만).
 */
public record PresencePush(String type, int count, List<Person> people) {

    /**
     * @param ip       가린 IP (예: 203.0.*.*)
     * @param host     방장인지
     * @param me       받는 사람 본인인지
     * @param joinedAt 들어온 시각(ms)
     */
    public record Person(String nickname, String ip, boolean host, boolean me, long joinedAt) {
    }

    public static PresencePush of(List<RoomSessionRegistry.Participant> participants, String hostId, String viewerId) {
        List<Person> people = participants.stream()
                .map(p -> new Person(p.nickname(), IpMask.mask(p.ip()), p.visitorId().equals(hostId),
                        p.visitorId().equals(viewerId), p.joinedAt()))
                .toList();
        return new PresencePush("presence", people.size(), people);
    }
}
