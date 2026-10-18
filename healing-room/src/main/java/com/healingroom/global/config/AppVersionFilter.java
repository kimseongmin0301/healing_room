package com.healingroom.global.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API 응답마다 서버 버전(X-App-Version)을 붙인다. 값은 서버가 켜진 시각이라 배포(재시작)할 때마다 바뀐다.
 * 화면은 로비·방을 오가도 페이지를 새로 불러오지 않아서, 탭을 오래 열어 두면 예전 코드가 계속 돈다
 * (내보내기 전 코드가 내보내진 뒤에도 1초마다 다시 접속한 적이 있음). 화면(api.js)은 이 값이 바뀌면 로비에서 새로고침한다.
 */
@Component
public class AppVersionFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-App-Version";

    private final String version = Long.toString(System.currentTimeMillis(), 36);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, version);
        chain.doFilter(request, response);
    }

    public String version() {
        return version;
    }
}
