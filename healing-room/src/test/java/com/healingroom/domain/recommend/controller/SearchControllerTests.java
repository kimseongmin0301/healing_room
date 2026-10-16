package com.healingroom.domain.recommend.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.recommend.dto.SearchResult;
import com.healingroom.domain.recommend.exception.SearchException;
import com.healingroom.domain.recommend.service.SearchService;

/** DB 없이 컨트롤러만 검증한다. */
@WebMvcTest(SearchController.class)
class SearchControllerTests {

    static final String VISITOR = "3f1c2a9e-5b7d-4e2a-9c1f-2a6b8d4e0f11";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SearchService searchService;

    @Test
    void returnsSearchResults() throws Exception {
        given(searchService.search("sea", VISITOR, "밤편지"))
                .willReturn(List.of(new SearchResult("abc", "아이유 - 밤편지", "IU", "t.jpg", 254)));

        mvc.perform(get("/api/rooms/sea/search").param("q", "밤편지").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].youtubeId").value("abc"))
                .andExpect(jsonPath("$[0].channel").value("IU"))
                .andExpect(jsonPath("$[0].seconds").value(254));
    }

    @Test
    void tooManySearchesIs429() throws Exception {
        given(searchService.search("sea", VISITOR, "밤편지")).willThrow(new SearchException.TooMany(5));

        mvc.perform(get("/api/rooms/sea/search").param("q", "밤편지").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("검색은 1분에 5번까지 할 수 있어요. 잠시 후 다시 해 주세요."));
    }

    @Test
    void quotaExhaustedIs503() throws Exception {
        given(searchService.search("sea", VISITOR, "밤편지")).willThrow(new SearchException.QuotaExhausted());

        mvc.perform(get("/api/rooms/sea/search").param("q", "밤편지").header("X-Visitor-Id", VISITOR))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("오늘 검색 횟수를 다 썼어요. YouTube 링크로 신청해 주세요."));
    }
}
