package com.example.ledgersystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

// 결제 파트로부터 받을 원장 기록 양식
public record PaymentRecordRequest(
        String transactionId,
        Long platformId,
        Long driverId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount
)
{
}