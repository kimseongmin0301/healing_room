package com.healingroom.domain.queue.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.queue.entity.QueueItem;
import com.healingroom.domain.queue.entity.QueueStatus;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

    Optional<QueueItem> findFirstByRoomIdAndStatusOrderByIdAsc(String roomId, QueueStatus status);

    List<QueueItem> findByRoomIdAndStatusOrderByIdAsc(String roomId, QueueStatus status);

    long countByRoomIdAndStatus(String roomId, QueueStatus status);

    List<QueueItem> findByStatusAndDurationSecondsNotNull(QueueStatus status);

    /** 지난 신청곡 (기본 음악 제외), 최근 것부터 */
    List<QueueItem> findTop30ByRoomIdAndAddedByIsNotNullAndStatusInOrderByIdDesc(String roomId,
                                                                               Collection<QueueStatus> statuses);

    /** 기본 음악 순환에 쓰는, 가장 최근에 넣은 기본 음악 */
    Optional<QueueItem> findFirstByRoomIdAndAddedByIsNullOrderByIdDesc(String roomId);
}
