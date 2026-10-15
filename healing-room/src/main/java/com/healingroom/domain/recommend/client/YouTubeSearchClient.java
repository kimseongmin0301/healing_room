package com.healingroom.domain.recommend.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.healingroom.domain.recommend.dto.VideoSuggestion;

import tools.jackson.databind.JsonNode;

/**
 * YouTube Data API v3 검색으로 비슷한 영상을 찾는다. YOUTUBE_API_KEY 가 없으면 아무것도 하지 않는다.
 *
 * <p>검색 한 번에 하루 할당량(기본 10,000) 중 100을 쓰므로, 같은 영상의 결과는 cache-hours 동안 메모리에 저장해 다시 쓴다.
 * (YouTube 가 "연관 영상" 검색 기능을 없애서, 지금 곡 제목으로 검색하는 방식을 쓴다)
 */
@Component
public class YouTubeSearchClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSearchClient.class);
    private static final String MUSIC_CATEGORY = "10"; // YouTube 영상 카테고리 "음악"

    private record Cached(Instant expiresAt, List<VideoSuggestion> videos) {
    }

    private final RestClient client;
    private final String apiKey;
    private final Duration cacheTtl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public YouTubeSearchClient(@Value("${healing-room.youtube.api-key:}") String apiKey,
                               @Value("${healing-room.youtube.search-cache-hours}") long cacheHours) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.cacheTtl = Duration.ofHours(cacheHours);
    }

    /** 저장해 둔 검색 결과를 모두 버린다 (초기화할 때) */
    public void clearCache() {
        cache.clear();
    }

    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    /** title 과 비슷한 영상 (다른 사이트에서 재생 가능한 것만). 키가 없거나 실패하면 빈 목록 */
    public List<VideoSuggestion> similarTo(String videoId, String title) {
        if (!isAvailable() || title == null || title.isBlank()) {
            return List.of();
        }
        Cached cached = cache.get(videoId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.videos();
        }
        try {
            JsonNode body = client.get()
                    .uri(builder -> builder.scheme("https").host("www.googleapis.com").path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("videoCategoryId", MUSIC_CATEGORY)
                            .queryParam("videoEmbeddable", "true")
                            .queryParam("safeSearch", "moderate")
                            .queryParam("maxResults", "10")
                            .queryParam("q", searchQuery(title))
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            List<VideoSuggestion> videos = new ArrayList<>();
            if (body != null) {
                for (JsonNode item : body.path("items")) {
                    String id = item.path("id").path("videoId").asString("");
                    // 라이브·예정 방송은 다른 사이트에서 재생이 막힌 경우가 많아(오류 150) 뺀다
                    boolean live = !"none".equals(item.path("snippet").path("liveBroadcastContent").asString("none"));
                    if (!id.isEmpty() && !id.equals(videoId) && !live) {
                        videos.add(new VideoSuggestion(id, unescape(item.path("snippet").path("title").asString("")), null));
                    }
                }
            }
            cache.put(videoId, new Cached(Instant.now().plus(cacheTtl), List.copyOf(videos)));
            return videos;
        } catch (RestClientException e) {
            // 할당량 초과(403)나 네트워크 오류여도 추천의 다른 부분은 보여줄 수 있도록 빈 목록
            log.warn("YouTube 검색 실패 (video={}): {}", videoId, e.getMessage());
            return List.of();
        }
    }

    /** 제목에서 [괄호], (괄호), | 뒤 채널명 같은 군더더기를 빼고 검색어로 쓴다 */
    static String searchQuery(String title) {
        String q = title.replaceAll("[\\[(【].*?[\\])】]", " ")
                .replaceAll("[|·•].*$", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s'&-]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return q.isEmpty() ? title : q;
    }

    /** YouTube 검색 결과 제목의 HTML 엔티티(&#39; 등) 풀기 */
    static String unescape(String text) {
        return text.replace("&#39;", "'").replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
    }
}
