package com.healingroom.domain.chat.service;

import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅에 붙이는 이미지. 파일로 저장하지 않고 data URL(data:image/webp;base64,...) 그대로 채팅 기록(Redis)에 넣는다.
 * 화면이 보내기 전에 줄이고 압축한다(최대 800px, WebP 또는 JPEG). 서버는 형식·크기·실제 내용만 확인한다.
 *
 * <ul>
 *   <li>JPEG·PNG·WebP 만. SVG 는 그림 안에 스크립트를 넣을 수 있어 받지 않는다</li>
 *   <li>머리말(data:image/…)만 믿지 않고, base64 를 풀어 첫 바이트가 그 형식인지 본다</li>
 *   <li>{@link #MAX_LENGTH} 글자까지 (약 120KB). 방마다 최근 100개를 보관하므로 크게 받지 않는다</li>
 * </ul>
 */
public final class ChatImage {

    /** data URL 전체 길이 상한 (base64 는 원본보다 약 4/3 배 길다 → 원본 약 120KB) */
    public static final int MAX_LENGTH = 160_000;

    private static final Pattern DATA_URL = Pattern.compile("^data:image/(jpeg|png|webp);base64,([A-Za-z0-9+/]+={0,2})$");

    private ChatImage() {
    }

    /** 받을 수 있는 이미지면 그대로, 아니면 비어 있다 (null·빈 값도 비어 있음) */
    public static Optional<String> clean(String dataUrl) {
        if (dataUrl == null || dataUrl.isEmpty() || dataUrl.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        Matcher m = DATA_URL.matcher(dataUrl);
        if (!m.matches()) {
            return Optional.empty();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return looksLike(m.group(1), bytes) ? Optional.of(dataUrl) : Optional.empty();
    }

    private static boolean looksLike(String type, byte[] b) {
        return switch (type) {
            case "jpeg" -> b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "webp" -> b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}
