package com.example.driverledgersystem.service;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LedgerService의 기사별 조회 및 원장 조회 격리 동작을 검증하는 통합 테스트입니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerReadBehaviorTest
{
    @Autowired
    private LedgerService ledgerService;

    /**
     * 원장 분개가 존재하지 않는 기사의 잔액이 0원인지 확인합니다.
     */
    @Test
    @DisplayName("원장 분개가 없는 기사의 미지급 잔액은 0원이다")
    void calculateDriverUnpaidBalanceReturnsZeroWhenNoEntriesExist()
    {
        BigDecimal balance =
                ledgerService.calculateDriverUnpaidBalance(201L);

        assertThat(balance)
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * 여러 기사에게 각각 발생한 원장 분개가
     * 기사별 잔액 계산에서 서로 영향을 주지 않는지 확인합니다.
     */
    @Test
    @DisplayName("기사별 원장 잔액은 서로 독립적으로 계산된다")
    void driverBalancesAreCalculatedIndependently()
    {
        Long firstDriverId = 202L;
        Long secondDriverId = 203L;

        List<LedgerEntryRequest.EntryDetail> firstDriverEntries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("15000"),
                        2001L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("15000"),
                        2001L,
                        "PLATFORM"
                )
        );

        List<LedgerEntryRequest.EntryDetail> secondDriverEntries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("20000"),
                        2002L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("20000"),
                        2002L,
                        "PLATFORM"
                )
        );

        ledgerService.recordEntries(
                "test-driver-isolation-first-001",
                firstDriverId,
                "PAYMENT",
                firstDriverEntries
        );

        ledgerService.recordEntries(
                "test-driver-isolation-second-001",
                secondDriverId,
                "PAYMENT",
                secondDriverEntries
        );

        BigDecimal firstDriverBalance =
                ledgerService.calculateDriverUnpaidBalance(firstDriverId);

        BigDecimal secondDriverBalance =
                ledgerService.calculateDriverUnpaidBalance(secondDriverId);

        assertThat(firstDriverBalance)
                .isEqualByComparingTo(new BigDecimal("15000"));

        assertThat(secondDriverBalance)
                .isEqualByComparingTo(new BigDecimal("20000"));
    }

    /**
     * 기사 결제 상세 조회에서 PAYMENT와 PAYMENT_CANCEL 분개가
     * 모두 포함되고 각 entryType이 정확하게 반환되는지 확인합니다.
     */
    @Test
    @DisplayName("결제 상세 조회에는 PAYMENT와 PAYMENT_CANCEL 분개가 포함된다")
    void getPaymentDetailsReturnsPaymentAndPaymentCancelEntries()
    {
        Long driverId = 204L;
        Long paymentId = 2003L;

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
                "test-payment-detail-payment-001",
                driverId,
                "PAYMENT",
                paymentEntries
        );

        List<LedgerEntryRequest.EntryDetail> cancelEntries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("5000"),
                        paymentId,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("5000"),
                        paymentId,
                        "PLATFORM"
                )
        );

        ledgerService.recordEntries(
                "test-payment-detail-cancel-001",
                driverId,
                "PAYMENT_CANCEL",
                cancelEntries
        );

        List<DriverLedgerResponse.PaymentDetail> paymentDetails =
                ledgerService.getPaymentDetails(driverId);

        assertThat(paymentDetails)
                .hasSize(2);

        assertThat(paymentDetails)
                .anySatisfy(detail ->
                {
                    assertThat(detail.getPaymentId())
                            .isEqualTo(paymentId);

                    assertThat(detail.getAmount())
                            .isEqualByComparingTo(new BigDecimal("15000"));

                    assertThat(detail.getEntryType())
                            .isEqualTo("PAYMENT");
                });

        assertThat(paymentDetails)
                .anySatisfy(detail ->
                {
                    assertThat(detail.getPaymentId())
                            .isEqualTo(paymentId);

                    assertThat(detail.getAmount())
                            .isEqualByComparingTo(new BigDecimal("5000"));

                    assertThat(detail.getEntryType())
                            .isEqualTo("PAYMENT_CANCEL");
                });
    }

    /**
     * 조회 기간에 분개가 존재하지 않는 경우
     * 원장 정합성 검증이 정상으로 처리되는지 확인합니다.
     */
    @Test
    @DisplayName("분개가 없는 기간의 원장 정합성 검증은 성공한다")
    void verifyLedgerIntegrityReturnsTrueWhenNoEntriesExist()
    {
        LocalDate from = LocalDate.of(2020, 1, 1);
        LocalDate to = LocalDate.of(2020, 1, 2);

        boolean balanced =
                ledgerService.verifyLedgerIntegrity(from, to);

        assertThat(balanced)
                .isTrue();
    }

    /**
     * 여러 기사에게 결제, 취소, 정산이 각각 발생해도
     * 기사별 최종 미지급 잔액이 서로 독립적으로 계산되는지 확인합니다.
     */
    @Test
    @DisplayName("다중 기사 복합 분개에서도 기사별 최종 잔액은 서로 독립적이다")
    void complexLedgerFlowsRemainIndependentBetweenDrivers()
    {
        Long firstDriverId = 205L;
        Long secondDriverId = 206L;

        ledgerService.recordEntries(
                "test-multi-driver-first-payment-001",
                firstDriverId,
                "PAYMENT",
                List.of(
                        new LedgerEntryRequest.EntryDetail(
                                "CREDIT",
                                new BigDecimal("15000"),
                                2101L,
                                "DRIVER"
                        ),
                        new LedgerEntryRequest.EntryDetail(
                                "DEBIT",
                                new BigDecimal("15000"),
                                2101L,
                                "PLATFORM"
                        )
                )
        );

        ledgerService.recordEntries(
                "test-multi-driver-first-cancel-001",
                firstDriverId,
                "PAYMENT_CANCEL",
                List.of(
                        new LedgerEntryRequest.EntryDetail(
                                "DEBIT",
                                new BigDecimal("5000"),
                                2101L,
                                "DRIVER"
                        ),
                        new LedgerEntryRequest.EntryDetail(
                                "CREDIT",
                                new BigDecimal("5000"),
                                2101L,
                                "PLATFORM"
                        )
                )
        );

        ledgerService.recordEntries(
                "test-multi-driver-first-settlement-001",
                firstDriverId,
                "SETTLEMENT",
                List.of(
                        new LedgerEntryRequest.EntryDetail(
                                "DEBIT",
                                new BigDecimal("4000"),
                                null,
                                "DRIVER"
                        ),
                        new LedgerEntryRequest.EntryDetail(
                                "CREDIT",
                                new BigDecimal("4000"),
                                null,
                                "PLATFORM"
                        )
                )
        );

        ledgerService.recordEntries(
                "test-multi-driver-second-payment-001",
                secondDriverId,
                "PAYMENT",
                List.of(
                        new LedgerEntryRequest.EntryDetail(
                                "CREDIT",
                                new BigDecimal("20000"),
                                2201L,
                                "DRIVER"
                        ),
                        new LedgerEntryRequest.EntryDetail(
                                "DEBIT",
                                new BigDecimal("20000"),
                                2201L,
                                "PLATFORM"
                        )
                )
        );

        ledgerService.recordEntries(
                "test-multi-driver-second-settlement-001",
                secondDriverId,
                "SETTLEMENT",
                List.of(
                        new LedgerEntryRequest.EntryDetail(
                                "DEBIT",
                                new BigDecimal("7000"),
                                null,
                                "DRIVER"
                        ),
                        new LedgerEntryRequest.EntryDetail(
                                "CREDIT",
                                new BigDecimal("7000"),
                                null,
                                "PLATFORM"
                        )
                )
        );

        BigDecimal firstDriverBalance =
                ledgerService.calculateDriverUnpaidBalance(firstDriverId);

        BigDecimal secondDriverBalance =
                ledgerService.calculateDriverUnpaidBalance(secondDriverId);

        assertThat(firstDriverBalance)
                .isEqualByComparingTo(new BigDecimal("6000"));

        assertThat(secondDriverBalance)
                .isEqualByComparingTo(new BigDecimal("13000"));

        UnpaidDriverListResponse unpaidList =
                ledgerService.getUnpaidBalances(LocalDate.now());

        assertThat(unpaidList.getData())
                .anyMatch(data ->
                        data.getDriverId().equals(firstDriverId)
                                && data.getTotalUnpaidAmount()
                                .compareTo(new BigDecimal("6000")) == 0
                );

        assertThat(unpaidList.getData())
                .anyMatch(data ->
                        data.getDriverId().equals(secondDriverId)
                                && data.getTotalUnpaidAmount()
                                .compareTo(new BigDecimal("13000")) == 0
                );
    }
}