package com.healingroom.domain.queue.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** room_queue_item.status 값. DB 에는 소문자로 저장한다. */
public enum QueueStatus {
    /** 대기 중 */
    QUEUED,
    /** 재생 중 (방마다 최대 1곡) */
    PLAYING,
    /** 끝까지 재생됨 */
    PLAYED,
    /** 재생 중에 투표로 넘김 */
    SKIPPED,
    /** 대기 중에 빠짐 (등록자가 빼거나 투표로 뺌) */
    REMOVED;

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<QueueStatus, String> {

        @Override
        public String convertToDatabaseColumn(QueueStatus status) {
            return status == null ? null : status.name().toLowerCase();
        }

        @Override
        public QueueStatus convertToEntityAttribute(String value) {
            return value == null ? null : QueueStatus.valueOf(value.toUpperCase());
        }
    }
}
