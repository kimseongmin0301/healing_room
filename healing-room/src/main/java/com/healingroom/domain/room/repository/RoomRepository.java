package com.healingroom.domain.room.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, String> {

    /** 열려 있는 방: 기본 방 먼저, 그다음 만든 순서 */
    List<Room> findByClosedAtIsNullOrderByIsDefaultDescCreatedAtAsc();

    Optional<Room> findByIdAndClosedAtIsNull(String id);

    boolean existsByIdAndClosedAtIsNull(String id);

    long countByCreatedByAndClosedAtIsNull(String createdBy);
}
