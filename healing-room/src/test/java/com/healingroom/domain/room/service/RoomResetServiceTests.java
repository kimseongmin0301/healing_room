package com.healingroom.domain.room.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import com.healingroom.domain.chat.service.ChatService;
import com.healingroom.domain.presence.service.RoomSessionRegistry;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.recommend.client.YouTubeSearchClient;
import com.healingroom.domain.room.exception.RoomException;

/** 기록 초기화 권한: 기본 방의 방장만 */
class RoomResetServiceTests {

    final RoomService rooms = mock(RoomService.class);
    final QueueService queue = mock(QueueService.class);
    final ChatService chat = mock(ChatService.class);
    final YouTubeSearchClient youtube = mock(YouTubeSearchClient.class);
    final RoomSessionRegistry sessions = mock(RoomSessionRegistry.class);
    final RoomResetService service = new RoomResetService(rooms, queue, chat, youtube, sessions);

    @Test
    void hostOfDefaultRoomCanReset() {
        given(rooms.isDefault("sea")).willReturn(true);
        given(sessions.isHost("sea", "A")).willReturn(true);

        service.resetByHost("sea", "A");

        verify(queue).resetRoom("sea");
        verify(chat).clearRoom("sea");
    }

    @Test
    void nonHostCannotReset() {
        given(rooms.isDefault("sea")).willReturn(true);
        given(sessions.isHost("sea", "B")).willReturn(false);

        assertThatThrownBy(() -> service.resetByHost("sea", "B")).isInstanceOf(RoomException.ResetNotAllowed.class);
        verifyNoInteractions(queue, chat);
    }

    @Test
    void userRoomsCannotBeResetEvenByHost() {
        given(rooms.isDefault("ab3k9xqz")).willReturn(false);
        given(sessions.isHost("ab3k9xqz", "A")).willReturn(true);

        assertThatThrownBy(() -> service.resetByHost("ab3k9xqz", "A")).isInstanceOf(RoomException.ResetNotAllowed.class);
        verifyNoInteractions(queue, chat);
    }

    @Test
    void resetAllAlsoClearsSearchCache() {
        service.resetAll();

        verify(queue).resetAll();
        verify(chat).clearAll();
        verify(youtube).clearCache();
    }
}
