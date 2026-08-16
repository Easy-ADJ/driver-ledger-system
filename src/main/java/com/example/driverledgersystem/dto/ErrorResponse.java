package com.example.driverledgersystem.dto;

public record ErrorResponse(
        String code,
        String message,
        String transactionId
)
{
}