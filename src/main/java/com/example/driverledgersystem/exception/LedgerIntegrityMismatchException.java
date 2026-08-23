package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class LedgerIntegrityMismatchException extends LedgerException
{
    public LedgerIntegrityMismatchException()
    {
        super(
                "LEDGER_INTEGRITY_MISMATCH",
                HttpStatus.CONFLICT,
                "원장 차변과 대변의 합이 일치하지 않습니다."
        );
    }
}