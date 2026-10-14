package com.healingroom.domain.presence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class RoomSessionRegistryTests {

    static final AtomicInteger SEQ = new AtomicInteger();
    final RoomSessionRegistry registry = new RoomSessionRegistry();

    static WebSocketSession session() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s" + SEQ.incrementAndGet());
        return session;
    }

    @Test
    void firstVisitorIsHostAndNextOneTakesOverWhenHostLeaves() {
        WebSocketSession a = session(), b = session(), c = session();
        registry.add("room", "A", a);
        registry.add("room", "B", b);
        registry.add("room", "C", c);
        assertThat(registry.host("room")).contains("A");

        registry.remove("room", a.getId());
        assertThat(registry.host("room")).contains("B");

        registry.remove("room", b.getId());
        assertThat(registry.host("room")).contains("C");
    }

    @Test
    void refreshingHostKeepsHostSeat() {
        WebSocketSession a = session(), b = session();
        registry.add("room", "A", a);
        registry.add("room", "B", b);

        registry.remove("room", a.getId());               // 새로고침으로 잠깐 끊김
        assertThat(registry.host("room")).contains("B");  // 그동안은 B 가 방장
        registry.add("room", "A", session());             // 바로 다시 들어옴
        assertThat(registry.host("room")).contains("A");  // 원래 순서대로 A 가 방장
    }

    @Test
    void sameVisitorWithTwoTabsCountsOnceAndStaysUntilLastTabCloses() {
        WebSocketSession tab1 = session(), tab2 = session(), other = session();
        registry.add("room", "A", tab1);
        registry.add("room", "A", tab2);
        registry.add("room", "B", other);
        assertThat(registry.listenerCount("room")).isEqualTo(2);

        registry.remove("room", tab1.getId());
        assertThat(registry.host("room")).contains("A");
        assertThat(registry.isHost("room", "B")).isFalse();
    }

    @Test
    void emptyRoomHasNoHost() {
        assertThat(registry.host("room")).isEmpty();
        assertThat(registry.isHost("room", "A")).isFalse();
    }
}
