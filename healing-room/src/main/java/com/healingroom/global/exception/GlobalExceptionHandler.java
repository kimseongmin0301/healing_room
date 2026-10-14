package com.healingroom.global.exception;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.healingroom.domain.queue.exception.QueueException;

/** API 오류를 { "message": "..." } 형태로 통일해서 내려준다. message 는 화면에 그대로 표시된다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(QueueException.class)
    public ResponseEntity<Map<String, String>> handleQueue(QueueException e) {
        return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
    }
}
