package com.healingroom.domain.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, String> {

    List<Room> findAllByOrderBySortOrderAsc();
}
