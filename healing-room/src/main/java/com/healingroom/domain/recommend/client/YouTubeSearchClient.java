package com.healingroom.domain.recommend.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.healingroom.domain.queue.util.SongTitles;
import com.healingroom.domain.recommend.dto.SearchResult;
import com.healingroom.domain.recommend.dto.VideoSuggestion;
import com.healingroom.domain.recommend.exception.SearchException;

import tools.jackson.databind.JsonNode;

/**
 * YouTube Data API v3 검색. YOUTUBE_API_KEY 가 없으면 아무것도 하지 않는다.
 * <ul>
 *   <li>{@link #similarTo}: 자동 추천·추천 탭의 "비슷한 영상" (YouTube 가 "연관 영상" 검색을 없애서 제목·가수로 검색)</li>
 *   <li>{@link #search}: 사용자가 직접 입력한 검색어</li>
 * </ul>
 *
 * <p>검색 한 번에 하루 할당량(기본 10,000) 중 100을 쓰므로 결과는 cache-hours 동안 메모리에 저장해 다시 쓴다.
 * 하루 할당량을 다 쓰면(403 quota) 할당량이 다시 채워지는 태평양 시간 자정까지 YouTube 를 부르지 않는다.
 */
@Component
public class YouTubeSearchClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSearchClient.class);
    private static final String MUSIC_CATEGORY = "10"; // YouTube 영상 카테고리 "음악"
    private static final ZoneId QUOTA_ZONE = ZoneId.of("America/Los_Angeles"); // 할당량이 다시 채워지는 기준 시간대

    private record Cached(Instant expiresAt, List<VideoSuggestion> videos) {
    }

    private record CachedSearch(Instant expiresAt, List<SearchResult> results) {
    }

    private final RestClient client;
    private final String apiKey;
    private final Duration cacheTtl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();
    private volatile Instant quotaResetAt = Instant.EPOCH;

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
        searchCache.clear();
    }

    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    /** 오늘 할당량을 다 썼으면 true */
    public boolean quotaExhausted() {
        return Instant.now().isBefore(quotaResetAt);
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
        if (quotaExhausted()) {
            return List.of();
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
            noteQuota(e);
            log.warn("YouTube 검색 실패 (video={}): {}", videoId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 사용자가 입력한 검색어로 영상을 찾는다. 다른 사이트에서 재생할 수 있고, 라이브가 아니고, 한국에서 막히지 않은 것만.
     * 같은 검색어는 cache-hours 동안 다시 쓴다. 할당량은 검색 1번(100) + 영상 정보 1번(1).
     *
     * @throws SearchException.Unavailable 키가 없거나 YouTube 에 연결하지 못했을 때
     * @throws SearchException.QuotaExhausted 오늘 할당량을 다 썼을 때
     */
    public List<SearchResult> search(String query) {
        if (!isAvailable()) {
            throw new SearchException.Unavailable();
        }
        String q = query.strip().replaceAll("\\s+", " ");
        String key = q.toLowerCase(Locale.ROOT);
        CachedSearch cached = searchCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.results();
        }
        if (quotaExhausted()) {
            throw new SearchException.QuotaExhausted();
        }
        try {
            JsonNode found = client.get()
                    .uri(builder -> builder.scheme("https").host("www.googleapis.com").path("/youtube/v3/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("videoEmbeddable", "true")
                            .queryParam("safeSearch", "moderate")
                            .queryParam("regionCode", "KR")
                            .queryParam("maxResults", "15")
                            .queryParam("q", q)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            Map<String, JsonNode> snippets = new LinkedHashMap<>();
            if (found != null) {
                for (JsonNode item : found.path("items")) {
                    String id = item.path("id").path("videoId").asString("");
                    if (!id.isEmpty() && "none".equals(item.path("snippet").path("liveBroadcastContent").asString("none"))) {
                        snippets.put(id, item.path("snippet"));
                    }
                }
            }
            List<SearchResult> results = snippets.isEmpty() ? List.of() : withDetails(snippets);
            searchCache.put(key, new CachedSearch(Instant.now().plus(cacheTtl), results));
            return results;
        } catch (RestClientException e) {
            log.warn("YouTube 직접 검색 실패: {}", e.getMessage());
            if (noteQuota(e)) {
                throw new SearchException.QuotaExhausted();
            }
            throw new SearchException.Unavailable();
        }
    }

    /** 검색 결과에 영상 길이를 붙이고, 외부 재생 금지·한국 차단·라이브(길이 0)를 뺀다 */
    private List<SearchResult> withDetails(Map<String, JsonNode> snippets) {
        JsonNode details = client.get()
                .uri(builder -> builder.scheme("https").host("www.googleapis.com").path("/youtube/v3/videos")
                        .queryParam("part", "contentDetails,status")
                        .queryParam("id", String.join(",", snippets.keySet()))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        if (details != null) {
            details.path("items").forEach(item -> byId.put(item.path("id").asString(""), item));
        }
        List<SearchResult> results = new ArrayList<>();
        snippets.forEach((id, snippet) -> {
            JsonNode detail = byId.get(id);
            if (detail == null || !detail.path("status").path("embeddable").asBoolean(false)) {
                return;
            }
            for (JsonNode region : detail.path("contentDetails").path("regionRestriction").path("blocked")) {
                if ("KR".equals(region.asString(""))) {
                    return;
                }
            }
            Integer seconds = parseSeconds(detail.path("contentDetails").path("duration").asString(""));
            if (seconds == null || seconds == 0) {
                return; // 라이브·예정 방송
            }
            JsonNode thumbs = snippet.path("thumbnails");
            String thumbnail = thumbs.path("medium").path("url").asString(thumbs.path("default").path("url").asString(""));
            results.add(new SearchResult(id, unescape(snippet.path("title").asString("")),
                    unescape(snippet.path("channelTitle").asString("")), thumbnail, seconds));
        });
        return List.copyOf(results);
    }

    /** ISO 8601 길이(PT1H2M3S) → 초. 읽을 수 없으면 null */
    static Integer parseSeconds(String iso) {
        try {
            return (int) Duration.parse(iso).getSeconds();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 할당량 초과 오류면 다음 리셋 시각(태평양 시간 자정)까지 YouTube 를 부르지 않도록 표시하고 true */
    private boolean noteQuota(RestClientException e) {
        if (e instanceof RestClientResponseException response && response.getStatusCode().value() == 403
                && response.getResponseBodyAsString().contains("quota")) {
            quotaResetAt = LocalDate.now(QUOTA_ZONE).plusDays(1).atStartOfDay(QUOTA_ZONE).toInstant();
            log.warn("YouTube 하루 할당량을 다 썼어요. {} 까지 YouTube 검색을 쉬어요", quotaResetAt);
            return true;
        }
        return false;
    }

    /**
     * 검색어 만들기. "가수 - 곡" 형태면 가수 이름으로 검색한다(곡 제목으로 찾으면 같은 노래의 다른 영상만 잔뜩 나온다).
     * 아니면 제목에서 [괄호], (괄호), | 뒤 채널명 같은 군더더기를 빼고 쓴다
     */
    static String searchQuery(String title) {
        String artist = SongTitles.artist(title)
                .replaceAll("[^\\p{L}\\p{N}\\s'&-]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (!artist.isEmpty()) {
            return artist;
        }
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
