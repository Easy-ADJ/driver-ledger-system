package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

/**
 * 원장 서버가 의도적으로 던지는 예외의 공통 부모입니다.
 *
 * 모든 하위 예외는 호출자와의 계약인 error code와
 * HTTP status를 보유합니다.
 */
public abstract class LedgerException extends RuntimeException
{
    private final String code;
    private final HttpStatus status;

    protected LedgerException(
            String code,
            HttpStatus status,
            String message
    )
    {
        this(
                code,
                status,
                message,
                null
        );
    }

    protected LedgerException(
            String code,
            HttpStatus status,
            String message,
            Throwable cause
    )
    {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode()
    {
        return code;
    }

    public HttpStatus getStatus()
    {
        return status;
    }
}