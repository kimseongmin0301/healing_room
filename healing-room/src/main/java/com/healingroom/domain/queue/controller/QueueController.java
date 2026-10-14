package com.healingroom.domain.queue.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.queue.client.YouTubeOEmbedClient;
import com.healingroom.domain.queue.dto.DurationRequest;
import com.healingroom.domain.queue.dto.MoveRequest;
import com.healingroom.domain.queue.dto.PlaybackRequest;
import com.healingroom.domain.queue.dto.PlayerState;
import com.healingroom.domain.queue.dto.QueueAddRequest;
import com.healingroom.domain.queue.dto.QueueAddResponse;
import com.healingroom.domain.queue.dto.QueueHistoryResponse;
import com.healingroom.domain.queue.exception.QueueException;
import com.healingroom.domain.queue.service.QueueService;
import com.healingroom.domain.queue.util.YouTubeLinks;

/**
 * 방 음악 대기열 API. 방문자는 X-Visitor-Id 헤더(브라우저에 저장된 UUID)로 구분한다.
 * 신청·투표·조작은 WebSocket 으로 방에 접속해 있는 방문자만 할 수 있고, 재생 조작과 순서 바꾸기는 방장만 할 수 있다.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/queue")
public class QueueController {

    static final String VISITOR_HEADER = "X-Visitor-Id";

    private final QueueService queueService;
    private final YouTubeOEmbedClient oEmbed;

    public QueueController(QueueService queueService, YouTubeOEmbedClient oEmbed) {
        this.queueService = queueService;
        this.oEmbed = oEmbed;
    }

    @GetMapping
    public PlayerState state(@PathVariable String roomId,
                             @RequestHeader(value = VISITOR_HEADER, required = false) String visitorId) {
        return queueService.stateFor(roomId, visitorId);
    }

    /** 지난 신청곡 (끝까지 재생 / 넘김 / 빠짐), 최근 것부터 */
    @GetMapping("/history")
    public List<QueueHistoryResponse> history(@PathVariable String roomId,
                                              @RequestHeader(value = VISITOR_HEADER, required = false) String visitorId) {
        return queueService.history(roomId, visitorId);
    }

    /** 곡 신청: YouTube 링크(또는 영상 id)를 받아 대기열 맨 뒤에 넣는다. */
    @PostMapping
    public ResponseEntity<QueueAddResponse> add(@PathVariable String roomId,
                                                @RequestHeader(VISITOR_HEADER) String visitorId,
                                                @RequestBody QueueAddRequest request) {
        String youtubeId = YouTubeLinks.videoId(request.url()).orElseThrow(QueueException.InvalidLink::new);
        String title = oEmbed.fetchTitle(youtubeId);
        long id = queueService.add(roomId, visitorId, youtubeId, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(new QueueAddResponse(id, youtubeId, title));
    }

    /** 넘기기/빼기 (내 곡이거나 방장이면 바로 처리, 아니면 투표) */
    @PutMapping("/{itemId}/vote")
    public Map<String, Boolean> vote(@PathVariable String roomId, @PathVariable Long itemId,
                                     @RequestHeader(VISITOR_HEADER) String visitorId) {
        return Map.of("applied", queueService.vote(roomId, itemId, visitorId));
    }

    @DeleteMapping("/{itemId}/vote")
    public ResponseEntity<Void> unvote(@PathVariable String roomId, @PathVariable Long itemId,
                                       @RequestHeader(VISITOR_HEADER) String visitorId) {
        queueService.unvote(roomId, itemId, visitorId);
        return ResponseEntity.noContent().build();
    }

    /** 플레이어가 알아낸 영상 길이. 서버는 이 값으로 곡이 끝나는 시점을 판단한다. */
    @PutMapping("/{itemId}/duration")
    public ResponseEntity<Void> duration(@PathVariable String roomId, @PathVariable Long itemId,
                                         @RequestHeader(VISITOR_HEADER) String visitorId,
                                         @RequestBody DurationRequest request) {
        queueService.reportDuration(roomId, itemId, visitorId, request.seconds());
        return ResponseEntity.noContent().build();
    }

    /** 방장: 모두의 재생 멈춤 / 이어서 재생 / 위치 이동 */
    @PutMapping("/playback")
    public ResponseEntity<Void> playback(@PathVariable String roomId,
                                         @RequestHeader(VISITOR_HEADER) String visitorId,
                                         @RequestBody PlaybackRequest request) {
        String action = request.action() == null ? "" : request.action();
        switch (action) {
            case "pause" -> queueService.pause(roomId, visitorId);
            case "resume" -> queueService.resume(roomId, visitorId);
            case "seek" -> queueService.seek(roomId, visitorId, request.seconds() == null ? 0 : request.seconds());
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.noContent().build();
    }

    /** 방장: 대기열에서 한 칸 앞/뒤로 */
    @PutMapping("/{itemId}/move")
    public ResponseEntity<Void> move(@PathVariable String roomId, @PathVariable Long itemId,
                                     @RequestHeader(VISITOR_HEADER) String visitorId,
                                     @RequestBody MoveRequest request) {
        QueueService.Direction direction;
        if ("up".equals(request.direction())) {
            direction = QueueService.Direction.UP;
        } else if ("down".equals(request.direction())) {
            direction = QueueService.Direction.DOWN;
        } else {
            return ResponseEntity.badRequest().build();
        }
        queueService.move(roomId, itemId, visitorId, direction);
        return ResponseEntity.noContent().build();
    }
}
