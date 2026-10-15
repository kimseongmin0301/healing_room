package com.healingroom.domain.queue.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 대기열 곡의 출처 (room_queue_item.source). DB 에는 소문자로 저장한다. */
public enum QueueSource {
    /** 방문자가 신청한 곡 */
    REQUEST,
    /** 테마 기본 음악 (처음 들어왔을 때, 추천을 못 찾았을 때) */
    DEFAULT,
    /** 신청곡이 없을 때 방금 나온 곡과 비슷한 영상을 자동으로 이어 튼 곡 */
    AUTOPLAY;

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<QueueSource, String> {

        @Override
        public String convertToDatabaseColumn(QueueSource source) {
            return source == null ? null : source.name().toLowerCase();
        }

        @Override
        public QueueSource convertToEntityAttribute(String value) {
            return value == null ? null : QueueSource.valueOf(value.toUpperCase());
        }
    }
}
