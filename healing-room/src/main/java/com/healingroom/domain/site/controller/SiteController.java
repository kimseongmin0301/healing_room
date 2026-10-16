package com.healingroom.domain.site.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 화면 주소를 깔끔하게(.html·# 없이) 보여준다.
 * <ul>
 *   <li>방: {@code /cafe}, {@code /ab3k9xqz} → 첫 화면(static/index.html). 화면이 주소를 보고 방에 들어간다</li>
 *   <li>소개 페이지: {@code /about}, {@code /themes}, {@code /themes/cafe} → resources/pages/ (scripts/seo_pages.py 로 만듦)</li>
 *   <li>관리자: {@code /admin} → resources/pages/admin.html</li>
 *   <li>예전 주소({@code /about.html}, {@code /themes/cafe.html} 등)는 새 주소로 영구 이동(301) — 검색엔진에 등록된 주소도 넘어오게</li>
 * </ul>
 * 고정 주소(/about, /themes, /admin)가 방 주소 규칙보다 먼저 맞는다. 방 id 는 소문자·숫자(사용자 방은 8자)라 겹치지 않는다.
 */
@Controller
public class SiteController {

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    @GetMapping("/{roomId:[a-z0-9][a-z0-9-]{0,11}}")
    public ResponseEntity<Resource> room(@PathVariable String roomId) {
        return html("static/index.html");
    }

    @GetMapping("/about")
    public ResponseEntity<Resource> about() {
        return html("pages/about.html");
    }

    @GetMapping("/admin")
    public ResponseEntity<Resource> admin() {
        return html("pages/admin.html");
    }

    @GetMapping({"/themes", "/themes/"})
    public ResponseEntity<Resource> themes() {
        return html("pages/themes/index.html");
    }

    @GetMapping("/themes/{slug:[a-z][a-z-]{0,30}}")
    public ResponseEntity<Resource> theme(@PathVariable String slug) {
        return html("pages/themes/" + slug + ".html");
    }

    // ---------------------------------------------------------------- 예전 주소 → 새 주소

    // /index.html 은 넣지 않는다: 스프링이 첫 화면 "/" 을 보여줄 때 안에서 /index.html 로 넘기므로 되돌리면 끝없이 돈다
    @GetMapping({"/about.html", "/admin.html"})
    public ResponseEntity<Void> legacyPage(HttpServletRequest request) {
        String path = request.getRequestURI();
        return moved(path.substring(0, path.length() - ".html".length()));
    }

    @GetMapping("/themes/index.html")
    public ResponseEntity<Void> legacyThemes() {
        return moved("/themes");
    }

    @GetMapping("/themes/{slug:[a-z][a-z-]{0,30}}.html")
    public ResponseEntity<Void> legacyTheme(@PathVariable String slug) {
        return moved("/themes/" + slug);
    }

    private static ResponseEntity<Void> moved(String location) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(location)).build();
    }

    /** 페이지가 없으면 404 (오류 화면 error/404.html 이 보인다) */
    private static ResponseEntity<Resource> html(String path) {
        Resource page = new ClassPathResource(path);
        if (!page.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.noCache()).body(page);
    }
}
