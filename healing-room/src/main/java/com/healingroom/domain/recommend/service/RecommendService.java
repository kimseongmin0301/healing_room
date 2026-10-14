package com.healingroom.domain.recommend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.healingroom.domain.queue.dto.PlayerState;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.recommend.client.YouTubeSearchClient;
import com.healingroom.domain.recommend.dto.RecommendationResponse;
import com.healingroom.domain.recommend.dto.VideoSuggestion;
import com.healingroom.domain.recommend.repository.RecommendRepository;
import com.healingroom.domain.room.service.RoomService;

/**
 * 곡 추천: 지금 재생 중인 곡을 기준으로
 * ① YouTube 에서 비슷한 영상 ② 이 곡과 함께 신청된 곡 ③ 이 테마 인기곡.
 * "내 신청 기록"은 브라우저(localStorage)에 있으므로 서버는 관여하지 않는다.
 */
@Service
public class RecommendService {

    private static final int LIMIT = 10;
    private static final int POPULAR_DAYS = 30;
    private static final int TOGETHER_HOURS = 3;

    private final RecommendRepository repository;
    private final YouTubeSearchClient youtube;
    private final RoomService roomService;
    private final QueueService queueService;

    public RecommendService(RecommendRepository repository, YouTubeSearchClient youtube,
                            RoomService roomService, QueueService queueService) {
        this.repository = repository;
        this.youtube = youtube;
        this.roomService = roomService;
        this.queueService = queueService;
    }

    public RecommendationResponse recommend(String roomId) {
        PlayerState.Track playing = queueService.stateFor(roomId, null).nowPlaying();
        String themeId = roomService.themeIdOf(roomId).orElse("");

        List<VideoSuggestion> popular = repository.popularInTheme(themeId, POPULAR_DAYS, LIMIT);
        if (playing == null) {
            return new RecommendationResponse(null, List.of(), youtube.isAvailable(), List.of(), popular);
        }
        return new RecommendationResponse(
                playing.title(),
                youtube.similarTo(playing.youtubeId(), playing.title()),
                youtube.isAvailable(),
                repository.requestedTogether(playing.youtubeId(), TOGETHER_HOURS, LIMIT),
                popular);
    }
}
