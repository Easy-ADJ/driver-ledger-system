package com.example.driverledgersystem;

import com.example.driverledgersystem.dto.LedgerEntryRequest;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceTestt
{
    @Autowired
    private LedgerService ledgerService;

    @Test
    @DisplayName("결제 및 상쇄 분개 기록 시 잔액 증감 및 미지급 목록 갱신 통합 테스트")
    void testRecordAndCalculateBalance()
    {
        // given
        String paymentIdempotencyKey = "test-pay-2026-08-21-001";
        String payoutIdempotencyKey = "test-payout-2026-08-21-002";
        Long driverId = 1L;
        Long paymentId = 100L;
        LocalDate today = LocalDate.now();

        // 💡 1. 결제 15,000 기록 테스트 (기사 미지급금 증가)
        List<LedgerEntryRequest.EntryDetail> paymentEntries = List.of(
                new LedgerEntryRequest.EntryDetail("CREDIT", new BigDecimal("15000"), paymentId, "DRIVER"),
                new LedgerEntryRequest.EntryDetail("DEBIT", new BigDecimal("15000"), paymentId, "PLATFORM")
        );
        ledgerService.recordPaymentEntry(paymentIdempotencyKey, driverId, "PAYMENT", paymentEntries);

        // then 1: 잔액 15,000 검증
        BigDecimal balanceAfterPayment = ledgerService.calculateDriverUnpaidBalance(driverId);
        assertThat(balanceAfterPayment).isEqualByComparingTo(new BigDecimal("15000"));

        // then 1-1: 상쇄 전엔 기사 1명이 미지급 목록에 포함되는지 검증
        UnpaidDriverListResponse unpaidListBefore = ledgerService.getUnpaidBalances(today);
        assertThat(unpaidListBefore.getData()).hasSize(1);
        assertThat(unpaidListBefore.getData().get(0).getDriverId()).isEqualTo(driverId);

        // 💡 2. 상쇄 15,000 기록 테스트 (기사 미지급금 차감)
        List<LedgerEntryRequest.EntryDetail> payoutEntries = List.of(
                new LedgerEntryRequest.EntryDetail("DEBIT", new BigDecimal("15000"), null, "DRIVER"),
                new LedgerEntryRequest.EntryDetail("CREDIT", new BigDecimal("15000"), null, "PLATFORM")
        );
        ledgerService.recordPayoutEntry(payoutIdempotencyKey, driverId, payoutEntries);

        // then 2: 잔액 0 검증
        BigDecimal balanceAfterPayout = ledgerService.calculateDriverUnpaidBalance(driverId);
        assertThat(balanceAfterPayout).isEqualByComparingTo(BigDecimal.ZERO);

        // then 2-1: 상쇄 후엔 빈 목록이 반환되는지 검증
        UnpaidDriverListResponse unpaidListAfter = ledgerService.getUnpaidBalances(today);
        assertThat(unpaidListAfter.getData()).isEmpty();
    }
}