package com.healingroom.room;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomResponse> findAll() {
        return roomRepository.findAllByOrderBySortOrderAsc().stream().map(RoomResponse::from).toList();
    }

    public Optional<RoomResponse> findById(String id) {
        return roomRepository.findById(id).map(RoomResponse::from);
    }

    public boolean exists(String id) {
        return roomRepository.existsById(id);
    }
}
