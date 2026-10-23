package com.healingroom.domain.chat.entity;

/**
 * Redis 에 JSON 으로 저장하는 채팅 메시지 한 개 (키: {prefix}chat:{roomId}, List).
 * authorId 는 서버 안에서만 쓰고 절대 브라우저로 내보내지 않는다.
 *
 * @param sentAt 보낸 시각(ms)
 * @param image  붙인 이미지 data URL (없으면 null. 이 칸이 생기기 전 기록도 null 로 읽힌다)
 */
public record ChatRecord(String id, String roomId, String authorId, String nickname, String text, long sentAt, String image) {

    public ChatRecord(String id, String roomId, String authorId, String nickname, String text, long sentAt) {
        this(id, roomId, authorId, nickname, text, sentAt, null);
    }
}
