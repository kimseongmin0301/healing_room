package com.healingroom.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class ChatImageTests {

    static String dataUrl(String type, byte[] bytes) {
        return "data:image/" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Test
    void acceptsRealJpegPngWebp() {
        assertThat(ChatImage.clean(dataUrl("jpeg", JPEG))).isPresent();
        assertThat(ChatImage.clean(dataUrl("png", PNG))).isPresent();
        assertThat(ChatImage.clean(dataUrl("webp", WEBP))).isPresent();
    }

    @Test
    void refusesSvgBecauseItCanCarryScript() {
        String svg = dataUrl("svg+xml", "<svg onload=alert(1)>".getBytes());
        assertThat(ChatImage.clean(svg)).isEmpty();
    }

    @Test
    void refusesWhenContentDoesNotMatchHeader() {
        assertThat(ChatImage.clean(dataUrl("png", JPEG))).isEmpty();          // 머리말은 PNG, 내용은 JPEG
        assertThat(ChatImage.clean(dataUrl("jpeg", "hello".getBytes()))).isEmpty();
        assertThat(ChatImage.clean("data:image/jpeg;base64,%%%")).isEmpty();
        assertThat(ChatImage.clean("https://example.com/a.jpg")).isEmpty();   // 바깥 주소는 받지 않음
        assertThat(ChatImage.clean(null)).isEmpty();
    }

    @Test
    void refusesTooLarge() {
        byte[] big = new byte[ChatImage.MAX_LENGTH];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        assertThat(ChatImage.clean(dataUrl("jpeg", big))).isEmpty();
    }
}
