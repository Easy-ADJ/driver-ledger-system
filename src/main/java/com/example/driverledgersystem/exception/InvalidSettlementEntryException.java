package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidSettlementEntryException extends LedgerException
{
    public InvalidSettlementEntryException()
    {
        super(
                "INVALID_SETTLEMENT_ENTRY",
                HttpStatus.BAD_REQUEST,
                "SETTLEMENT 분개의 paymentId는 null이어야 합니다."
        );
    }
}