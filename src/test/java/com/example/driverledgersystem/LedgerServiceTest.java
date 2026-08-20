package com.example.driverledgersystem;

import com.example.driverledgersystem.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LedgerServiceTest
{
    @Autowired
    private LedgerService ledgerService;

    @Test
    @DisplayName("결제 분개 기록 및 미지급금 잔액 계산 테스트")
    void testRecordAndCalculateBalance()
    {
        // given
        String idempotencyKey = "test-key-2026-08-17-001";
        Long driverId = 1L;
        Long paymentId = 100L;
        BigDecimal amount = new BigDecimal("15000");

        // when: 결제 분개 기록 실행
        ledgerService.recordPaymentEntry(idempotencyKey, driverId, paymentId, amount);

        // then: 해당 기사의 미지급금 잔액이 대변(15000) - 차변(0) = 15000인지 검증
        BigDecimal balance = ledgerService.calculateDriverUnpaidBalance(driverId);

        assertThat(balance).isEqualByComparingTo(new BigDecimal("15000"));
    }
}