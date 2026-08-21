package com.example.driverledgersystem.dto;

/**
 * Ledger API의 공통 오류 응답을 담는 DTO입니다.
 *
 * @param code          오류 코드
 * @param message       오류 메시지
 * @param transactionId 요청 또는 거래 식별자
 */
public record ErrorResponse(
        String code,
        String message,
        String transactionId
)
{
}