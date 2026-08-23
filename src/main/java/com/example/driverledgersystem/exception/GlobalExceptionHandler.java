package com.example.driverledgersystem.exception;

import com.example.driverledgersystem.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * 원장 서버에서 발생하는 예외를 공통 오류 형식으로 변환합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    private static final String INTERNAL_ERROR_MESSAGE =
            "원장 서버 내부 오류가 발생했습니다.";

    /**
     * 원장 서버에서 의도적으로 발생시킨 비즈니스 예외를 처리합니다.
     */
    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ErrorResponse> handleLedgerException(
            LedgerException e
    )
    {
        String transactionId =
                currentTransactionId();

        log.warn(
                "[{}] {}: {}",
                transactionId,
                e.getCode(),
                e.getMessage()
        );

        return respond(
                e.getStatus(),
                e.getCode(),
                e.getMessage(),
                transactionId
        );
    }

    /**
     * 잘못된 JSON 요청을 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException e
    )
    {
        String transactionId =
                currentTransactionId();

        String message =
                "요청 본문 형식이 올바르지 않습니다.";

        log.warn(
                "[{}] INVALID_REQUEST: {}",
                transactionId,
                message
        );

        return respond(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                message,
                transactionId
        );
    }

    /**
     * 필수 쿼리 파라미터 누락을 처리합니다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e
    )
    {
        String transactionId =
                currentTransactionId();

        String message =
                "필수 파라미터가 누락되었습니다: "
                        + e.getParameterName();

        log.warn(
                "[{}] MISSING_REQUIRED_PARAMETER: {}",
                transactionId,
                message
        );

        return respond(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUIRED_PARAMETER",
                message,
                transactionId
        );
    }

    /**
     * 날짜 등 쿼리 파라미터 형식 오류를 처리합니다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e
    )
    {
        String transactionId =
                currentTransactionId();

        String message =
                "파라미터 형식이 올바르지 않습니다: "
                        + e.getName();

        log.warn(
                "[{}] INVALID_PARAMETER_FORMAT: {}",
                transactionId,
                message
        );

        return respond(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_FORMAT",
                message,
                transactionId
        );
    }

    /**
     * 예상하지 못한 예외를 처리합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e
    )
    {
        String transactionId =
                currentTransactionId();

        log.error(
                "[{}] INTERNAL_ERROR",
                transactionId,
                e
        );

        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                INTERNAL_ERROR_MESSAGE,
                transactionId
        );
    }

    private String currentTransactionId()
    {
        return UUID.randomUUID().toString();
    }

    private static ResponseEntity<ErrorResponse> respond(
            HttpStatus status,
            String code,
            String message,
            String transactionId
    )
    {
        return ResponseEntity
                .status(status)
                .body(
                        new ErrorResponse(
                                code,
                                message,
                                transactionId
                        )
                );
    }
}