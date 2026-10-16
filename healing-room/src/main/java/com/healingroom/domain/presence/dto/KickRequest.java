package com.healingroom.domain.presence.dto;

/** @param key 접속자 목록의 가린 키 (방문자 id 가 아님) */
public record KickRequest(String key) {
}
