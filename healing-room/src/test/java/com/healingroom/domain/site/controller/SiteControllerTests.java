package com.healingroom.domain.site.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.healingroom.domain.room.service.RoomService;

/** 깔끔한 주소(.html·# 없이)와 예전 주소 이동을 확인한다. 페이지 파일은 실제 resources 의 것을 읽는다 */
@WebMvcTest(SiteController.class)
class SiteControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RoomService roomService;

    @Test
    void openRoomPathShowsMainPage() throws Exception {
        given(roomService.isOpen("cafe")).willReturn(true);
        given(roomService.isOpen("ab3k9xqz")).willReturn(true);

        mvc.perform(get("/cafe"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("id=\"lobby\"")));
        mvc.perform(get("/ab3k9xqz")).andExpect(status().isOk());
    }

    @Test
    void unknownOrClosedRoomIs404() throws Exception {
        // 없는 주소를 로비로 슬쩍 보내지 않고 404 화면을 보여준다
        mvc.perform(get("/asdf")).andExpect(status().isNotFound());
    }

    @Test
    void pagesWithoutHtmlExtension() throws Exception {
        mvc.perform(get("/about")).andExpect(status().isOk()).andExpect(content().string(containsString("힐링룸 소개")));
        mvc.perform(get("/themes")).andExpect(status().isOk()).andExpect(content().string(containsString("테마 모음")));
        mvc.perform(get("/themes/cafe")).andExpect(status().isOk()).andExpect(content().string(containsString("카페멍")));
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(content().string(containsString("관리자")));
    }

    @Test
    void unknownThemeIs404() throws Exception {
        mvc.perform(get("/themes/nope")).andExpect(status().isNotFound());
    }

    @Test
    void oldHtmlAddressesMovePermanently() throws Exception {
        mvc.perform(get("/about.html")).andExpect(status().isMovedPermanently()).andExpect(header().string("Location", "/about"));
        mvc.perform(get("/admin.html")).andExpect(status().isMovedPermanently()).andExpect(header().string("Location", "/admin"));
        mvc.perform(get("/themes/index.html")).andExpect(status().isMovedPermanently()).andExpect(header().string("Location", "/themes"));
        mvc.perform(get("/themes/cafe.html")).andExpect(status().isMovedPermanently()).andExpect(header().string("Location", "/themes/cafe"));
    }
}
