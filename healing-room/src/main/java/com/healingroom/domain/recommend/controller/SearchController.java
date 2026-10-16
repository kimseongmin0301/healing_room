package com.healingroom.domain.recommend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.recommend.dto.SearchResult;
import com.healingroom.domain.recommend.service.SearchService;

@RestController
@RequestMapping("/api/rooms/{roomId}/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** YouTube 에서 곡 검색 (이 사이트에서 재생 가능한 영상만). 방에 접속해 있어야 하고 1분에 몇 번으로 제한된다 */
    @GetMapping
    public List<SearchResult> search(@PathVariable String roomId,
                                     @RequestParam(defaultValue = "") String q,
                                     @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return searchService.search(roomId, visitorId, q);
    }
}
