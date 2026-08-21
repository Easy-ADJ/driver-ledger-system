package com.example.driverledgersystem.exception;

import com.example.driverledgersystem.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Ledger API에서 발생하는 예외를 공통 형식으로 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{
    /**
     * 요청 본문을 JSON으로 변환할 수 없는 경우 발생한 예외를 처리합니다.
     *
     * @param e 발생한 예외
     * @return 잘못된 요청 본문에 대한 오류 응답
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    )
    {
        log.warn(
                "요청 본문 JSON 파싱 실패: {}",
                e.getMessage()
        );

        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                "요청 본문 형식이 올바르지 않습니다.",
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * 잘못된 요청 값으로 발생한 예외를 처리합니다.
     *
     * @param e 발생한 예외
     * @return 잘못된 요청에 대한 오류 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e
    )
    {
        log.warn(
                "잘못된 요청 발생: {}",
                e.getMessage()
        );

        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                e.getMessage(),
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * 처리되지 않은 서버 내부 예외를 처리합니다.
     *
     * @param e 발생한 예외
     * @return 서버 내부 오류 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(
            Exception e
    )
    {
        log.error(
                "서버 내부 오류 발생",
                e
        );

        ErrorResponse response = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "원장 서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                null
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}