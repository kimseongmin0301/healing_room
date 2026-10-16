package com.healingroom.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * robots.txt, llms.txt 같은 .txt 정적 파일을 "text/plain;charset=UTF-8" 로 보낸다.
 * 문자셋이 없으면 일부 수집기(AI 크롤러 등)가 한글을 깨진 글자로 읽는다.
 */
@Component
public class TextFileCharsetFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().endsWith(".txt");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        chain.doFilter(request, response);
    }
}
