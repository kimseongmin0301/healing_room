package com.healingroom.domain.presence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.healingroom.domain.presence.exception.PresenceException;

import tools.jackson.databind.json.JsonMapper;

class KickServiceTests {

    static final AtomicInteger ids = new AtomicInteger();

    RoomSessionRegistry registry = new RoomSessionRegistry();
    KickService service = new KickService(registry, JsonMapper.builder().build());

    static WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s" + ids.incrementAndGet());
        return session;
    }

    @Test
    void hostKicksAndTheyCannotComeBackForTenMinutes() throws Exception {
        WebSocketSession host = session(), guest = session(), guestTab2 = session();
        registry.add("sea", "HOST", host);
        registry.add("sea", "GUEST", guest);
        registry.add("sea", "GUEST", guestTab2);

        service.kickByHost("sea", "HOST", registry.keyOf("sea", "GUEST"));

        // 그 사람의 탭 모두에 알림을 보내고 4001 로 끊는다
        for (WebSocketSession tab : new WebSocketSession[] {guest, guestTab2}) {
            verify(tab).sendMessage(argThat(m -> ((TextMessage) m).getPayload().contains("\"type\":\"kicked\"")
                    && ((TextMessage) m).getPayload().contains("\"by\":\"host\"")));
            verify(tab).close(KickService.CLOSE);
        }
        verify(host, never()).close(KickService.CLOSE);
        assertThat(registry.bannedUntil("sea", "GUEST")).isPresent();
        assertThat(registry.bannedUntil("ocean", "GUEST")).isEmpty(); // 다른 방은 상관없음
    }

    @Test
    void onlyHostCanKickAndNotThemselves() {
        registry.add("sea", "HOST", session());
        registry.add("sea", "GUEST", session());

        assertThatThrownBy(() -> service.kickByHost("sea", "GUEST", registry.keyOf("sea", "HOST")))
                .isInstanceOf(PresenceException.HostOnly.class);
        assertThatThrownBy(() -> service.kickByHost("sea", "HOST", registry.keyOf("sea", "HOST")))
                .isInstanceOf(PresenceException.NotYourself.class);
        assertThatThrownBy(() -> service.kickByHost("sea", "HOST", "없는키"))
                .isInstanceOf(PresenceException.NotHere.class);
    }

    @Test
    void adminCanKickAnyoneIncludingHost() throws Exception {
        WebSocketSession host = session();
        registry.add("sea", "HOST", host);

        service.kickByAdmin("sea", registry.keyOf("sea", "HOST"));

        verify(host).sendMessage(argThat(m -> ((TextMessage) m).getPayload().contains("\"by\":\"admin\"")));
        verify(host).close(KickService.CLOSE);
    }

    @Test
    void keyDoesNotRevealVisitorId() {
        String key = registry.keyOf("sea", "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11");
        assertThat(key).hasSize(16).doesNotContain("3f1c2a9e");
        assertThat(registry.keyOf("cafe", "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11")).isNotEqualTo(key); // 방마다 다름
    }
}
