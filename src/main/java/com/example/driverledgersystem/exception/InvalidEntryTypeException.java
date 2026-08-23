package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidEntryTypeException extends LedgerException
{
    public InvalidEntryTypeException()
    {
        super(
                "INVALID_ENTRY_TYPE",
                HttpStatus.BAD_REQUEST,
                "지원하지 않는 entryType입니다."
        );
    }
}