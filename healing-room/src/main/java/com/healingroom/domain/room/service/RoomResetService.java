package com.healingroom.domain.room.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.healingroom.domain.chat.service.ChatService;
import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.recommend.client.YouTubeSearchClient;
import com.healingroom.domain.room.exception.RoomException;

/**
 * 기록 초기화: 이상한 영상이 올라왔을 때 등 방의 흔적을 지운다.
 * 지우는 것: 대기열·지난 신청곡·투표(→ 인기곡·함께 신청된 곡·자동 추천 후보에서도 사라짐), 채팅.
 * 전체 초기화는 YouTube 검색 캐시도 비운다. 지운 뒤 사람이 있는 방은 테마 기본 음악부터 다시 튼다.
 *
 * <p>방문자 중에서는 <b>기본 방의 방장</b>만 할 수 있다 (사용자 방은 30분 비면 저절로 닫히므로 대상 아님).
 * 관리자는 아무 방이나, 또는 전체를 초기화할 수 있다.
 */
@Service
public class RoomResetService {

    private final RoomService roomService;
    private final QueueService queueService;
    private final ChatService chatService;
    private final YouTubeSearchClient youtube;
    private final RoomSessionRegistry sessions;

    public RoomResetService(RoomService roomService, QueueService queueService, ChatService chatService,
                            YouTubeSearchClient youtube, RoomSessionRegistry sessions) {
        this.roomService = roomService;
        this.queueService = queueService;
        this.chatService = chatService;
        this.youtube = youtube;
        this.sessions = sessions;
    }

    /** 방장이 누른 초기화 버튼 */
    public void resetByHost(String roomId, String visitorId) {
        if (!roomService.isDefault(roomId) || !sessions.isHost(roomId, visitorId)) {
            throw new RoomException.ResetNotAllowed();
        }
        reset(roomId);
    }

    /** 방 하나 초기화 (관리자) */
    public Map<String, Object> reset(String roomId) {
        int songs = queueService.resetRoom(roomId);
        chatService.clearRoom(roomId);
        return Map.of("room", roomId, "deletedSongs", songs);
    }

    /** 모든 방 초기화 (관리자) */
    public Map<String, Object> resetAll() {
        int songs = queueService.resetAll();
        int chats = chatService.clearAll();
        youtube.clearCache();
        return Map.of("room", "*", "deletedSongs", songs, "deletedChatRooms", chats);
    }
}
