package com.healingroom.domain.queue.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.healingroom.domain.queue.entity.QueueItem;
import com.healingroom.domain.queue.entity.QueueStatus;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

    /** 재생 중인 곡 (방마다 최대 1곡) */
    Optional<QueueItem> findFirstByRoomIdAndStatusOrderByIdAsc(String roomId, QueueStatus status);

    /** 대기열 (순서대로) */
    List<QueueItem> findByRoomIdAndStatusOrderByQueueOrderAsc(String roomId, QueueStatus status);

    Optional<QueueItem> findFirstByRoomIdAndStatusOrderByQueueOrderAsc(String roomId, QueueStatus status);

    long countByRoomIdAndStatus(String roomId, QueueStatus status);

    @Query("select coalesce(max(q.queueOrder), 0) from QueueItem q where q.roomId = :roomId")
    long maxQueueOrder(@Param("roomId") String roomId);

    List<QueueItem> findByStatusAndDurationSecondsNotNull(QueueStatus status);

    /** 기본 음악 순환에 쓰는, 가장 최근에 넣은 기본 음악 */
    Optional<QueueItem> findFirstByRoomIdAndAddedByIsNullOrderByIdDesc(String roomId);

    /** 지난 신청곡 (기본 음악 제외), 최근 것부터 */
    List<QueueItem> findTop30ByRoomIdAndAddedByIsNotNullAndStatusInOrderByIdDesc(String roomId,
                                                                               Collection<QueueStatus> statuses);
}
