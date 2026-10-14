package com.healingroom.domain.queue.dto;

import com.healingroom.domain.queue.entity.QueueItem;

/**
 * 지난 신청곡 한 줄.
 *
 * @param status  played(끝까지 재생) / skipped(넘김) / removed(빠짐)
 * @param endedAt 끝난 시각(ms)
 */
public record QueueHistoryResponse(long id, String youtubeId, String title, String status, boolean mine, Long endedAt) {

    public static QueueHistoryResponse of(QueueItem item, String visitorId) {
        return new QueueHistoryResponse(item.getId(), item.getYoutubeId(), item.getTitle(),
                item.getStatus().name().toLowerCase(), item.isAddedBy(visitorId),
                item.getEndedAt() == null ? null : item.getEndedAt().toInstant().toEpochMilli());
    }
}
