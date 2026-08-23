package com.example.driverledgersystem.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends LedgerException
{
    private InvalidDateRangeException(String message)
    {
        super(
                "INVALID_DATE_RANGE",
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    public static InvalidDateRangeException missingPair()
    {
        return new InvalidDateRangeException(
                "from과 to는 함께 지정해야 합니다."
        );
    }

    public static InvalidDateRangeException reversed()
    {
        return new InvalidDateRangeException(
                "조회 시작 날짜는 종료 날짜보다 이후일 수 없습니다."
        );
    }
}