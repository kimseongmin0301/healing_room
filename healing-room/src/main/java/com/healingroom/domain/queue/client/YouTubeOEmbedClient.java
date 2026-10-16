package com.healingroom.domain.queue.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.healingroom.domain.queue.exception.QueueException;

/**
 * YouTube oEmbed 로 영상 제목을 가져온다. API 키가 필요 없다.
 * 영상이 없거나 다른 사이트에 넣을 수 없게(임베드 금지) 설정돼 있으면 실패로 본다.
 */
@Component
public class YouTubeOEmbedClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeOEmbedClient.class);

    private final RestClient client;

    public YouTubeOEmbedClient() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public String fetchTitle(String videoId) {
        try {
            Map<?, ?> body = client.get()
                    .uri("https://www.youtube.com/oembed?format=json&url={url}",
                            "https://www.youtube.com/watch?v=" + videoId)
                    .retrieve()
                    .body(Map.class);
            Object title = body == null ? null : body.get("title");
            if (title == null) {
                throw new QueueException.VideoUnavailable();
            }
            String text = title.toString();
            return text.length() > 200 ? text.substring(0, 200) : text;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // 어떤 영상이 왜 막혔는지 나중에 확인할 수 있게 남긴다 (영상 id 는 개인정보가 아니다)
            log.info("영상 확인 실패 (video={}, oEmbed {})", videoId, status);
            if (status == 401 || status == 403) {
                throw new QueueException.EmbedBlocked();   // 업로더가 다른 사이트 재생을 막음
            }
            if (status == 400 || status == 404) {
                throw new QueueException.VideoNotFound();  // 없거나 비공개
            }
            throw new QueueException.VideoUnavailable();
        } catch (RestClientException e) {
            log.warn("YouTube 영상 확인 연결 실패 (video={}): {}", videoId, e.getMessage());
            throw new QueueException.YouTubeUnreachable();
        }
    }
}
