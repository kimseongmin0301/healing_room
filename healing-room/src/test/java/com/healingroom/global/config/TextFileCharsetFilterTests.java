package com.healingroom.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TextFileCharsetFilterTests {

    TextFileCharsetFilter filter = new TextFileCharsetFilter();

    @Test
    void textFilesAreSentAsUtf8() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/llms.txt"), response, new MockFilterChain());
        response.setContentType("text/plain");
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
    }

    @Test
    void otherRequestsAreLeftAlone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/rooms"), response, new MockFilterChain());
        assertThat(response.isCharset()).isFalse();
    }
}
