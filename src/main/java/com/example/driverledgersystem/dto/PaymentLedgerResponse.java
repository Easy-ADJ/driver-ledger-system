package com.example.driverledgersystem.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 서버에서 조회한 기사별 결제 내역을 담습니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PaymentLedgerResponse
{
    private Long paymentId;
    private BigDecimal amount;
    private LocalDateTime approvedAt;
}