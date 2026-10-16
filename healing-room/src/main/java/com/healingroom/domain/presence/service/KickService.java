package com.healingroom.domain.presence.service;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.healingroom.domain.presence.dto.KickedPush;
import com.healingroom.domain.presence.exception.PresenceException;

import tools.jackson.databind.json.JsonMapper;

/**
 * 방에서 내보내기. 방장은 자기 방에서, 관리자는 어느 방에서나 내보낼 수 있다.
 * 내보낸 사람은 {@link #BAN}(10분) 동안 그 방에 다시 못 들어온다 (서버 메모리, 방문자 id 기준).
 * 내보낸 사람의 연결(탭)마다 {@link KickedPush} 를 보내고 {@link #CLOSE} 코드로 끊는다 → 화면은 로비로 가서 슬픈 고양이 창을 띄운다.
 * 연결이 끊기면 RoomSocketHandler 가 접속자 목록·방장을 다시 정해 방 전체에 알린다.
 */
@Service
public class KickService {

    public static final Duration BAN = Duration.ofMinutes(10);
    /** 내보내져서 끊긴 연결 (화면이 다시 연결하지 않게 구분한다) */
    public static final CloseStatus CLOSE = new CloseStatus(4001, "kicked");

    private static final Logger log = LoggerFactory.getLogger(KickService.class);

    private final RoomSessionRegistry sessions;
    private final JsonMapper json;

    public KickService(RoomSessionRegistry sessions, JsonMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    /** 방장이 내보내기. targetKey 는 접속자 목록의 가린 키 */
    public void kickByHost(String roomId, String hostVisitorId, String targetKey) {
        if (hostVisitorId == null || !sessions.isHost(roomId, hostVisitorId)) {
            throw new PresenceException.HostOnly();
        }
        String target = sessions.visitorByKey(roomId, targetKey).orElseThrow(PresenceException.NotHere::new);
        if (target.equals(hostVisitorId)) {
            throw new PresenceException.NotYourself();
        }
        kick(roomId, target, "host");
    }

    /** 관리자가 내보내기 (권한 확인은 관리자 컨트롤러에서) */
    public void kickByAdmin(String roomId, String targetKey) {
        String target = sessions.visitorByKey(roomId, targetKey).orElseThrow(PresenceException.NotHere::new);
        kick(roomId, target, "admin");
    }

    private void kick(String roomId, String visitorId, String by) {
        long until = System.currentTimeMillis() + BAN.toMillis();
        sessions.ban(roomId, visitorId, until);
        String text = json.writeValueAsString(KickedPush.of(by, until));
        for (WebSocketSession session : sessions.sessionsOf(roomId, visitorId)) {
            close(session, text);
        }
        log.info("방에서 내보냄 (room={}, by={})", roomId, by);
    }

    /** 다시 들어오려는 연결에도 같은 알림을 보내고 끊는다 */
    public void refuse(WebSocketSession session, long until) {
        close(session, json.writeValueAsString(KickedPush.of("ban", until)));
    }

    private static void close(WebSocketSession session, String text) {
        try {
            session.sendMessage(new TextMessage(text));
        } catch (IOException | IllegalStateException e) {
            // 이미 끊긴 연결이면 알림 없이 닫기만 한다
        }
        try {
            session.close(CLOSE);
        } catch (IOException e) {
            // 무시
        }
    }
}
