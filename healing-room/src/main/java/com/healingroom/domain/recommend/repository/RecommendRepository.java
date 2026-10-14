package com.healingroom.domain.recommend.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.healingroom.domain.recommend.dto.VideoSuggestion;

/**
 * 신청 기록(room_queue_item)으로 추천 목록을 만든다. 집계 쿼리라 JPA 대신 SQL 로 조회한다.
 * 기본 음악(added_by IS NULL)은 세지 않는다.
 */
@Repository
public class RecommendRepository {

    private final JdbcClient jdbc;

    public RecommendRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 이 테마의 방들에서 최근 days 일 동안 많이 신청된 곡 */
    public List<VideoSuggestion> popularInTheme(String themeId, int days, int limit) {
        return jdbc.sql("""
                        SELECT q.youtube_id, MAX(q.title) AS title, COUNT(*) AS cnt
                        FROM room_queue_item q
                        JOIN room r ON r.id = q.room_id
                        WHERE r.theme_id = :themeId
                          AND q.added_by IS NOT NULL
                          AND q.created_at > now() - make_interval(days => :days)
                        GROUP BY q.youtube_id
                        ORDER BY cnt DESC, MAX(q.created_at) DESC
                        LIMIT :limit
                        """)
                .param("themeId", themeId)
                .param("days", days)
                .param("limit", limit)
                .query((rs, n) -> new VideoSuggestion(rs.getString("youtube_id"), rs.getString("title"), rs.getInt("cnt")))
                .list();
    }

    /** youtubeId 가 신청된 방에서 앞뒤 hours 시간 안에 함께 신청된 다른 곡 (함께 신청된 방이 많은 순) */
    public List<VideoSuggestion> requestedTogether(String youtubeId, int hours, int limit) {
        return jdbc.sql("""
                        SELECT q2.youtube_id, MAX(q2.title) AS title, COUNT(DISTINCT q2.room_id) AS cnt
                        FROM room_queue_item q1
                        JOIN room_queue_item q2
                          ON q2.room_id = q1.room_id
                         AND q2.youtube_id <> q1.youtube_id
                         AND q2.added_by IS NOT NULL
                         AND q2.created_at BETWEEN q1.created_at - make_interval(hours => :hours)
                                               AND q1.created_at + make_interval(hours => :hours)
                        WHERE q1.youtube_id = :youtubeId
                          AND q1.added_by IS NOT NULL
                        GROUP BY q2.youtube_id
                        ORDER BY cnt DESC, MAX(q2.created_at) DESC
                        LIMIT :limit
                        """)
                .param("youtubeId", youtubeId)
                .param("hours", hours)
                .param("limit", limit)
                .query((rs, n) -> new VideoSuggestion(rs.getString("youtube_id"), rs.getString("title"), rs.getInt("cnt")))
                .list();
    }
}
