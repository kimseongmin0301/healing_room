package com.healingroom.domain.room.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.room.dto.RoomDetailResponse;
import com.healingroom.domain.room.dto.RoomResponse;
import com.healingroom.domain.room.repository.RoomAmbientRepository;
import com.healingroom.domain.room.repository.RoomRepository;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomAmbientRepository ambientRepository;
    private final RoomSessionRegistry sessions;

    public RoomService(RoomRepository roomRepository, RoomAmbientRepository ambientRepository,
                       RoomSessionRegistry sessions) {
        this.roomRepository = roomRepository;
        this.ambientRepository = ambientRepository;
        this.sessions = sessions;
    }

    public List<RoomResponse> findAll() {
        return roomRepository.findAllByOrderBySortOrderAsc().stream()
                .map(room -> RoomResponse.of(room, sessions.listenerCount(room.getId())))
                .toList();
    }

    public Optional<RoomDetailResponse> findDetail(String id) {
        return roomRepository.findById(id).map(room -> new RoomDetailResponse(
                RoomResponse.of(room, sessions.listenerCount(id)),
                ambientRepository.findByRoomIdOrderBySortOrderAsc(id).stream()
                        .map(RoomDetailResponse.Ambient::from).toList()));
    }

    public boolean exists(String id) {
        return roomRepository.existsById(id);
    }
}
