package com.healingroom.domain.moderation.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.healingroom.domain.moderation.dto.BanResponse;
import com.healingroom.domain.moderation.exception.ModerationException;
import com.healingroom.domain.presence.service.KickService;

/**
 * 욕설·음란어 제한. 채팅·닉네임·방 이름에 {@link ProfanityFilter} 가 찾는 말이 있으면
 * 그 IP 와 방문자 번호를 {@link #BAN}(30분) 동안 막는다: 채팅·방 만들기·방 입장이 안 되고, 들어가 있던 방에서도 나간다.
 *
 * <p>IP 와 방문자 번호 둘 다로 막는다(번호만이면 시크릿 창으로, IP 만이면 휴대폰 데이터 전환으로 바로 피한다).
 * PC방·회사처럼 IP 를 같이 쓰는 곳은 옆 사람도 함께 막히므로, 관리자 화면에서 바로 풀 수 있다.
 * 제한 목록은 서버 메모리에만 둔다(재시작하면 풀림). IP·쓴 글은 로그에 남기지 않는다.
 */
@Service
public class ModerationService {

    public static final Duration BAN = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final ProfanityFilter filter;
    private final KickService kickService;
    private final Map<String, Ban> bans = new ConcurrentHashMap<>();

    /**
     * @param where 어디서 걸렸는지 (chat, nickname, room-name)
     * @param word  걸린 말 (관리자 화면에서 오탐인지 보려고)
     */
    record Ban(String id, String visitorId, String ip, String where, String word, long at, long until) {

        boolean matches(String visitor, String address) {
            return (visitor != null && visitor.equals(visitorId)) || (address != null && address.equals(ip));
        }
    }

    public ModerationService(ProfanityFilter filter, KickService kickService) {
        this.filter = filter;
        this.kickService = kickService;
    }

    /**
     * 제한 중이면 막고, 글에 욕설·음란어가 있으면 제한을 건 뒤 막는다. 괜찮으면 아무 일도 없다.
     *
     * @param texts 검사할 글들 (null 은 건너뜀)
     */
    public void check(String visitorId, String ip, String where, String... texts) {
        requireNotBanned(visitorId, ip);
        for (String text : texts) {
            Optional<String> word = filter.find(text);
            if (word.isPresent()) {
                long until = ban(visitorId, ip, where, word.get());
                throw new ModerationException.Banned(until);
            }
        }
    }

    /** 평범한 말에도 들어 있는 애매한 말(mask.txt)은 제한하지 않고 * 로 가린다 */
    public String mask(String text) {
        return filter.mask(text);
    }

    /** 욕이 있는지만 본다 (WebSocket 처럼 예외 대신 직접 처리할 때). 있으면 제한을 걸고 풀리는 시각을 돌려준다 */
    public Optional<Long> banIfProfane(String visitorId, String ip, String where, String text) {
        return filter.find(text).map(word -> ban(visitorId, ip, where, word));
    }

    public void requireNotBanned(String visitorId, String ip) {
        bannedUntil(visitorId, ip).ifPresent(until -> {
            throw new ModerationException.Banned(until);
        });
    }

    /** 제한 중이면 풀리는 시각(ms) */
    public Optional<Long> bannedUntil(String visitorId, String ip) {
        long now = System.currentTimeMillis();
        bans.values().removeIf(ban -> ban.until() <= now);
        return bans.values().stream().filter(ban -> ban.matches(visitorId, ip)).map(Ban::until).max(Long::compare);
    }

    private long ban(String visitorId, String ip, String where, String word) {
        long now = System.currentTimeMillis();
        long until = now + BAN.toMillis();
        Ban ban = new Ban(UUID.randomUUID().toString(), visitorId, ip, where, word, now, until);
        bans.put(ban.id(), ban);
        kickService.removeEverywhere(visitorId, ip, until);
        log.info("욕설·음란어로 30분 이용 제한 (where={}, word={})", where, word);
        return until;
    }

    // ---------------------------------------------------------------- 관리자

    /** 지금 제한 중인 목록 (최근 것부터) */
    public List<BanResponse> list() {
        long now = System.currentTimeMillis();
        bans.values().removeIf(ban -> ban.until() <= now);
        return bans.values().stream()
                .sorted(Comparator.comparingLong(Ban::at).reversed())
                .map(ban -> new BanResponse(ban.id(), ban.where(), ban.word(),
                        ban.visitorId() == null ? null : ban.visitorId().substring(0, Math.min(8, ban.visitorId().length())),
                        ban.ip(), ban.at(), ban.until()))
                .toList();
    }

    /** 제한 풀기 (오탐이거나 IP 를 같이 쓰는 다른 사람이 막혔을 때) */
    public void lift(String id) {
        if (bans.remove(id) == null) {
            throw new ModerationException.NotFound();
        }
    }
}
