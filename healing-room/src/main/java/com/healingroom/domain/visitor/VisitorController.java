package com.healingroom.domain.visitor;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.global.util.ClientRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 화면이 시작할 때 부른다. 이 브라우저의 방문자 id 를 정해 돌려주고 쿠키에도 남긴다.
 * ① 쿠키가 있으면 쿠키 id ② 없으면 화면이 저장소에 갖고 있던 id(X-Visitor-Id) ③ 둘 다 없으면 새로 만든다.
 */
@RestController
public class VisitorController {

    @GetMapping("/api/visitor")
    public ResponseEntity<Map<String, String>> visitor(
            @CookieValue(value = VisitorCookie.NAME, required = false) String cookieId,
            @RequestHeader(value = "X-Visitor-Id", required = false) String headerId,
            HttpServletRequest request) {
        String id = VisitorCookie.isUuid(cookieId) ? cookieId
                : VisitorCookie.isUuid(headerId) ? headerId : UUID.randomUUID().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, VisitorCookie.of(id, ClientRequest.secure(request)).toString())
                .body(Map.of("id", id));
    }
}
