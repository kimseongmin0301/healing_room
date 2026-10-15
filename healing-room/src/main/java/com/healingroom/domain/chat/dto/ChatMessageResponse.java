package com.healingroom.domain.chat.dto;

import com.healingroom.domain.chat.entity.ChatRecord;

/**
 * 화면에 보여줄 채팅 메시지. 보낸 사람의 방문자 id 대신 "내가 보낸 것인지"만 알려준다.
 *
 * @param sentAt 보낸 시각(ms)
 */
public record ChatMessageResponse(String id, String nickname, String text, long sentAt, boolean mine) {

    public static ChatMessageResponse of(ChatRecord record, String visitorId) {
        return new ChatMessageResponse(record.id(), record.nickname(), record.text(), record.sentAt(),
                record.authorId().equals(visitorId));
    }
}
