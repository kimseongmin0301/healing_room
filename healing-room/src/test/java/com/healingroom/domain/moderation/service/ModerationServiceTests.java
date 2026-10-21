package com.healingroom.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import org.springframework.data.redis.RedisConnectionFailureException;

import com.healingroom.domain.moderation.entity.BanRecord;
import com.healingroom.domain.moderation.exception.ModerationException;
import com.healingroom.domain.moderation.repository.BanRepository;
import com.healingroom.domain.presence.service.KickService;

class ModerationServiceTests {

    /** Redis 대신 메모리에 두는 가짜 저장소 (TTL 은 저장한 값만 확인) */
    static class MemoryBanRepository extends BanRepository {
        final Map<String, BanRecord> bans = new ConcurrentHashMap<>();
        Duration lastTtl;

        MemoryBanRepository() {
            super(null, null, "test:");
        }

        @Override public void save(BanRecord ban, Duration ttl) { bans.put(ban.id(), ban); lastTtl = ttl; }
        @Override public List<BanRecord> findFor(String visitorId, String ip) {
            return bans.values().stream().filter(b -> b.matches(visitorId, ip)).toList();
        }
        @Override public Optional<BanRecord> find(String id) { return Optional.ofNullable(bans.get(id)); }
        @Override public List<BanRecord> findAll() { return List.copyOf(bans.values()); }
        @Override public void delete(BanRecord ban) { bans.remove(ban.id()); }
    }

    static final ProfanityFilter FILTER = new ProfanityFilter(List.of("씨발"), List.of("시발점"));
    KickService kickService = mock(KickService.class);
    MemoryBanRepository repository = new MemoryBanRepository();
    ModerationService service = new ModerationService(FILTER, kickService, repository);

    @Test
    void banIsSavedWithThirtyMinuteTtl() {
        service.banIfProfane("V1", "1.2.3.4", "chat", "씨발");
        assertThat(repository.lastTtl).isEqualTo(ModerationService.BAN);
    }

    @Test
    void whenRedisIsDownServiceKeepsWorkingAndProfanityIsStillRefused() {
        BanRepository broken = mock(BanRepository.class);
        org.mockito.BDDMockito.willThrow(new RedisConnectionFailureException("down")).given(broken).findFor(any(), any());
        org.mockito.BDDMockito.willThrow(new RedisConnectionFailureException("down")).given(broken).save(any(), any());
        ModerationService withBrokenRedis = new ModerationService(FILTER, kickService, broken);

        // 확인이 안 돼도 깨끗한 글은 지나가고
        withBrokenRedis.check("V1", "1.2.3.4", "chat", "안녕하세요");
        // 지금 걸린 글은 거절하고 방에서도 내보낸다
        assertThatThrownBy(() -> withBrokenRedis.check("V1", "1.2.3.4", "chat", "씨발"))
                .isInstanceOf(ModerationException.Banned.class);
        verify(kickService).removeEverywhere(eq("V1"), eq("1.2.3.4"), anyLong());
    }

    @Test
    void cleanTextPasses() {
        service.check("V1", "1.2.3.4", "chat", "졸린 고양이", "안녕하세요");
        assertThat(service.bannedUntil("V1", "1.2.3.4")).isEmpty();
        verify(kickService, never()).removeEverywhere(eq("V1"), eq("1.2.3.4"), anyLong());
    }

    @Test
    void profanityBansIpAndVisitorForThirtyMinutesAndKicksEverywhere() {
        assertThatThrownBy(() -> service.check("V1", "1.2.3.4", "chat", "졸린 고양이", "씨 발"))
                .isInstanceOf(ModerationException.Banned.class)
                .hasMessageContaining("제한");
        verify(kickService).removeEverywhere(eq("V1"), eq("1.2.3.4"), anyLong());

        long until = service.bannedUntil("V1", null).orElseThrow();
        assertThat(until - System.currentTimeMillis()).isBetween(ModerationService.BAN.toMillis() - 5000, ModerationService.BAN.toMillis());
        // 방문자 번호를 바꿔도(시크릿 창) 같은 IP 면 막히고, IP 를 바꿔도(데이터 전환) 같은 번호면 막힌다
        assertThat(service.bannedUntil("OTHER", "1.2.3.4")).isPresent();
        assertThat(service.bannedUntil("V1", "9.9.9.9")).isPresent();
        assertThat(service.bannedUntil("OTHER", "9.9.9.9")).isEmpty();
        // 제한 중에는 깨끗한 글도 막는다
        assertThatThrownBy(() -> service.check("OTHER", "1.2.3.4", "chat", "안녕"))
                .isInstanceOf(ModerationException.Banned.class);
    }

    @Test
    void adminCanLiftBan() {
        assertThat(service.banIfProfane("V1", "1.2.3.4", "nickname", "씨발")).isPresent();
        var bans = service.list();
        assertThat(bans).hasSize(1);
        assertThat(bans.get(0).word()).isEqualTo("씨발");
        assertThat(bans.get(0).where()).isEqualTo("nickname");
        assertThat(bans.get(0).ip()).isEqualTo("1.2.3.4");

        service.lift(bans.get(0).id());

        assertThat(service.bannedUntil("V1", "1.2.3.4")).isEmpty();
        assertThatThrownBy(() -> service.lift(bans.get(0).id())).isInstanceOf(ModerationException.NotFound.class);
    }

    @Test
    void allowedWordsPass() {
        service.check("V1", "1.2.3.4", "chat", "시발점부터");
        assertThat(service.bannedUntil("V1", "1.2.3.4")).isEmpty();
    }
}
