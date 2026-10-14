package com.healingroom.domain.queue.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.healingroom.domain.queue.entity.QueueItem;
import com.healingroom.domain.queue.service.VotePolicy;

/**
 * 방의 음악 상태. 방문자마다 "내가 방장인지 / 내가 신청한 곡인지 / 내가 투표했는지"가 달라서 받는 사람 기준으로 만든다.
 * 다른 방문자의 id 는 절대 내보내지 않는다.
 *
 * @param serverTime 서버 현재 시각(ms). 브라우저가 시계 차이를 보정하는 데 쓴다
 * @param listeners  지금 방에 있는 사람 수
 * @param required   넘기기/빼기에 필요한 표 수
 * @param hostIsMe   받는 사람이 방장인지 (방장은 재생 조작, 바로 넘기기/빼기, 순서 바꾸기 가능)
 */
public record PlayerState(
        String type,
        long serverTime,
        int listeners,
        int required,
        boolean hostIsMe,
        Track nowPlaying,
        List<Track> queue
) {

    /**
     * @param startedAt 재생 시작 시각(ms). 대기 중이면 null
     * @param pausedAt  방장이 멈춘 시각(ms). 재생 중이면 null. 위치 = (pausedAt 또는 지금) - startedAt
     * @param duration  영상 길이(초). 모르거나 라이브면 null
     */
    public record Track(
            long id,
            String youtubeId,
            String title,
            Long startedAt,
            Long pausedAt,
            Integer duration,
            boolean isDefault,
            boolean mine,
            int votes,
            boolean votedByMe
    ) {
    }

    /** 방 전체 상태 (모든 방문자 공통). voters, hostId 는 서버 안에서만 쓴다 */
    public record Snapshot(QueueItem nowPlaying, List<QueueItem> queue, Map<Long, Set<String>> voters) {

        public PlayerState forVisitor(String visitorId, Set<String> present, String hostId, long now) {
            boolean hostIsMe = visitorId != null && visitorId.equals(hostId);
            return new PlayerState("state", now, present.size(), VotePolicy.required(present.size()), hostIsMe,
                    nowPlaying == null ? null : track(nowPlaying, visitorId, present),
                    queue.stream().map(item -> track(item, visitorId, present)).toList());
        }

        private Track track(QueueItem item, String visitorId, Set<String> present) {
            Set<String> itemVoters = voters.getOrDefault(item.getId(), Set.of());
            // 방을 떠난 사람의 표는 세지 않는다
            int votes = (int) itemVoters.stream().filter(present::contains).count();
            return new Track(item.getId(), item.getYoutubeId(), item.getTitle(),
                    item.getStartedAt() == null ? null : item.getStartedAt().toInstant().toEpochMilli(),
                    item.getPausedAt() == null ? null : item.getPausedAt().toInstant().toEpochMilli(),
                    item.getDurationSeconds(), item.isDefault(), item.isAddedBy(visitorId),
                    votes, visitorId != null && itemVoters.contains(visitorId));
        }
    }
}
