package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

/**
 * 원장 요청의 필수값이 누락된 경우 발생합니다.
 */
public class InvalidLedgerRequestException extends LedgerException
{
    private InvalidLedgerRequestException(
            String code,
            String message
    )
    {
        super(
                code,
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    public static InvalidLedgerRequestException missingIdempotencyKey()
    {
        return new InvalidLedgerRequestException(
                "MISSING_IDEMPOTENCY_KEY",
                "Idempotency-Key는 필수입니다."
        );
    }

    public static InvalidLedgerRequestException missingDriverId()
    {
        return new InvalidLedgerRequestException(
                "MISSING_DRIVER_ID",
                "driverId는 필수입니다."
        );
    }

    public static InvalidLedgerRequestException missingPaymentId()
    {
        return new InvalidLedgerRequestException(
                "MISSING_PAYMENT_ID",
                "paymentId는 필수입니다."
        );
    }

    public static InvalidLedgerRequestException missingApprovedAt()
    {
        return new InvalidLedgerRequestException(
                "MISSING_APPROVED_AT",
                "approvedAt은 필수입니다."
        );
    }

    public static InvalidLedgerRequestException emptyEntries()
    {
        return new InvalidLedgerRequestException(
                "EMPTY_ENTRIES",
                "분개 목록은 비어 있을 수 없습니다."
        );
    }

    public static InvalidLedgerRequestException nullEntry()
    {
        return new InvalidLedgerRequestException(
                "INVALID_ENTRY",
                "분개 내역은 null일 수 없습니다."
        );
    }
}