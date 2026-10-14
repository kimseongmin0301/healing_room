package com.healingroom.domain.queue.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

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
            // 401/403: 임베드 금지, 400/404: 없는 영상
            throw new QueueException.VideoUnavailable();
        } catch (RestClientException e) {
            throw new QueueException.YouTubeUnreachable();
        }
    }
}
