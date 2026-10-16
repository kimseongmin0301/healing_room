package com.healingroom.domain.admin.dto;

import java.util.List;

/** 관리자 화면: 지금 접속해 있는 모든 방과 사람 (전체 IP 포함) */
public record AdminPresenceResponse(int totalPeople, List<Room> rooms) {

    public record Room(String id, String name, int count, List<Person> people) {
    }

    /**
     * @param visitor 방문자 id 앞 8자리 (같은 사람이 여러 방·닉네임을 쓰는지 구분용)
     * @param tabs    같은 사람이 연 탭 수
     * @param key     내보내기에 쓰는 가린 키
     */
    public record Person(String key, String nickname, String ip, String visitor, boolean host, long joinedAt, int tabs) {
    }
}
