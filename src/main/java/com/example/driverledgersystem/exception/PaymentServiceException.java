package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

/**
 * 결제 서버 호출 실패 시 발생합니다.
 */
public class PaymentServiceException extends LedgerException
{
    public PaymentServiceException(
            String detail,
            Throwable cause
    )
    {
        super(
                "PAYMENT_SERVICE_UNAVAILABLE",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "결제 서버 호출 실패 - " + detail,
                cause
        );
    }
}