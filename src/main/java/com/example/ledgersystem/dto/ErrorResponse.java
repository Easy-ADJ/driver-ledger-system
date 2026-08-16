package com.example.ledgersystem.dto;

public record ErrorResponse(
        String code,
        String message,
        String transactionId
)
{
}