package com.healingroom.domain.moderation.entity;

/**
 * 욕설·음란어 이용 제한 하나 (Redis 에 JSON 으로 저장, 30분 뒤 자동으로 사라짐).
 *
 * @param where 어디서 걸렸는지 (chat, nickname, room-name)
 * @param word  걸린 말 (관리자 화면에서 오탐인지 보려고. 쓴 글 전체는 두지 않는다)
 * @param at    걸린 시각(ms)
 * @param until 풀리는 시각(ms)
 */
public record BanRecord(String id, String visitorId, String ip, String where, String word, long at, long until) {

    public boolean matches(String visitor, String address) {
        return (visitor != null && visitor.equals(visitorId)) || (address != null && address.equals(ip));
    }
}
