package com.example.driverledgersystem.exception; // 패키지명은 프로젝트에 맞게 확인하세요!

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j // 로그 기록용 어노테이션
@RestControllerAdvice
public class GlobalExceptionHandler
{
    // 400 에러 처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e)
    {
        log.warn("잘못된 요청 발생: {}", e.getMessage());
        Map<String, String> response = new HashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 500 서버 내부 에러 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception e)
    {
        // 에러 원인이 찍히도록 강제 로깅
        log.error("서버 내부 에러 발생!", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "원장 서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}