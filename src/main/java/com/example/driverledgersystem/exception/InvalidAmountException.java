package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidAmountException extends LedgerException
{
    public InvalidAmountException(String message)
    {
        super(
                "INVALID_AMOUNT",
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}