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

/**
 * LedgerService의 결제 및 정산 분개 처리를 검증하는 통합 테스트입니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceTest
{
    @Autowired
    private LedgerService ledgerService;

    /**
     * 결제 분개로 증가한 기사 미지급금이 정산 분개 이후 정상적으로 상쇄되는지 검증합니다.
     */
    @Test
    @DisplayName("결제 및 정산 분개 기록 시 잔액 증감 및 미지급 목록 갱신 통합 테스트")
    void testRecordAndCalculateBalance()
    {
        String paymentIdempotencyKey = "test-pay-2026-08-21-001";
        String settlementIdempotencyKey = "test-settlement-2026-08-21-002";
        Long driverId = 1L;
        Long paymentId = 100L;
        LocalDate today = LocalDate.now();

        List<LedgerEntryRequest.EntryDetail> paymentEntries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("15000"),
                        paymentId,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("15000"),
                        paymentId,
                        "PLATFORM"
                )
        );

        ledgerService.recordEntries(
                paymentIdempotencyKey,
                driverId,
                "PAYMENT",
                paymentEntries
        );

        BigDecimal balanceAfterPayment =
                ledgerService.calculateDriverUnpaidBalance(driverId);

        assertThat(balanceAfterPayment)
                .isEqualByComparingTo(new BigDecimal("15000"));

        UnpaidDriverListResponse unpaidListBefore =
                ledgerService.getUnpaidBalances(today);

        assertThat(unpaidListBefore.getData()).hasSize(1);
        assertThat(unpaidListBefore.getData().get(0).getDriverId())
                .isEqualTo(driverId);

        List<LedgerEntryRequest.EntryDetail> settlementEntries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("15000"),
                        null,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("15000"),
                        null,
                        "PLATFORM"
                )
        );

        ledgerService.recordEntries(
                settlementIdempotencyKey,
                driverId,
                "SETTLEMENT",
                settlementEntries
        );

        BigDecimal balanceAfterSettlement =
                ledgerService.calculateDriverUnpaidBalance(driverId);

        assertThat(balanceAfterSettlement)
                .isEqualByComparingTo(BigDecimal.ZERO);

        UnpaidDriverListResponse unpaidListAfter =
                ledgerService.getUnpaidBalances(today);

        assertThat(unpaidListAfter.getData()).isEmpty();
    }
}