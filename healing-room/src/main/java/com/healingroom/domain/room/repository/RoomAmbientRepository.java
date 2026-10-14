package com.healingroom.domain.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.room.entity.RoomAmbient;

public interface RoomAmbientRepository extends JpaRepository<RoomAmbient, Long> {

    List<RoomAmbient> findByRoomIdOrderBySortOrderAsc(String roomId);
}
