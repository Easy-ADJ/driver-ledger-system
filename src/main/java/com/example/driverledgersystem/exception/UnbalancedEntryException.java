package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class UnbalancedEntryException extends LedgerException
{
    public UnbalancedEntryException()
    {
        super(
                "UNBALANCED_ENTRY",
                HttpStatus.BAD_REQUEST,
                "차변과 대변의 합이 일치하지 않습니다."
        );
    }
}