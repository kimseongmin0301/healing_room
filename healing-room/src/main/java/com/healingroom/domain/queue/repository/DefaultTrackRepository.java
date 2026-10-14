package com.healingroom.domain.queue.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.queue.entity.DefaultTrack;

public interface DefaultTrackRepository extends JpaRepository<DefaultTrack, Long> {

    List<DefaultTrack> findByRoomIdOrderBySortOrderAsc(String roomId);
}
