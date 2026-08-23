package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidDirectionException extends LedgerException
{
    public InvalidDirectionException()
    {
        super(
                "INVALID_DIRECTION",
                HttpStatus.BAD_REQUEST,
                "direction은 DEBIT 또는 CREDIT이어야 합니다."
        );
    }
}