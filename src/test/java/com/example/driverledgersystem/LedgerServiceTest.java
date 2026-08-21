package com.example.driverledgersystem;

import com.example.driverledgersystem.dto.LedgerEntryRequest;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.service.LedgerService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 원장 분개 기록, 잔액 계산, 정합성 검증 및 입력값 검증에 대한 통합 테스트입니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerServiceTest
{
    @Autowired
    private LedgerService ledgerService;

    /**
     * 결제 및 정산 분개 기록에 따라 기사 미지급 잔액과
     * 미지급 기사 목록이 정상적으로 갱신되는지 확인합니다.
     */
    @Test
    @DisplayName("결제 및 정산 분개 기록 시 잔액 증감 및 미지급 목록 갱신 통합 테스트")
    void testRecordAndCalculateBalance()
    {
        Long driverId = 1L;
        Long paymentId = 100L;
        LocalDate today = LocalDate.now();

        String paymentIdempotencyKey = "test-payment-001";

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

        assertThat(unpaidListBefore.getData())
                .hasSize(1);

        assertThat(unpaidListBefore.getData().get(0).getDriverId())
                .isEqualTo(driverId);

        assertThat(unpaidListBefore.getData().get(0).getTotalUnpaidAmount())
                .isEqualByComparingTo(new BigDecimal("15000"));

        String settlementIdempotencyKey = "test-settlement-001";

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

        assertThat(unpaidListAfter.getData())
                .isEmpty();
    }

    /**
     * 균형 잡힌 차변과 대변 분개가 존재할 때
     * 원장 정합성 검증이 성공하는지 확인합니다.
     */
    @Test
    @DisplayName("기간 내 차변과 대변 합계가 일치하면 정합성 검증에 성공한다")
    void verifyLedgerIntegrityReturnsTrueWhenBalanced()
    {
        String idempotencyKey = "test-verify-001";
        Long driverId = 2L;
        Long paymentId = 200L;
        LocalDate today = LocalDate.now();

        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("10000"),
                        paymentId,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000"),
                        paymentId,
                        "PLATFORM"
                )
        );

        ledgerService.recordEntries(
                idempotencyKey,
                driverId,
                "PAYMENT",
                entries
        );

        boolean balanced =
                ledgerService.verifyLedgerIntegrity(today, today);

        assertThat(balanced).isTrue();
    }

    /**
     * 조회 시작 날짜가 종료 날짜보다 이후인 경우
     * 정합성 검증 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("조회 시작 날짜가 종료 날짜보다 이후이면 정합성 검증에 실패한다")
    void verifyLedgerIntegrityThrowsExceptionWhenFromIsAfterTo()
    {
        LocalDate from = LocalDate.of(2026, 8, 22);
        LocalDate to = LocalDate.of(2026, 8, 21);

        assertThatThrownBy(
                () -> ledgerService.verifyLedgerIntegrity(from, to)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 시작 날짜는 종료 날짜보다 이후일 수 없습니다.");
    }

    /**
     * 소수 금액이 포함된 분개 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("원장 금액에 소수점이 포함되면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenAmountHasDecimal()
    {
        String idempotencyKey = "test-decimal-001";
        Long driverId = 3L;
        Long paymentId = 300L;

        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("10000.5"),
                        paymentId,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000.5"),
                        paymentId,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        idempotencyKey,
                        driverId,
                        "PAYMENT",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("원장 금액은 1원 단위의 정수여야 합니다.");
    }

    /**
     * 차변과 대변의 합계가 일치하지 않는 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("차변과 대변의 합계가 일치하지 않으면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenDebitAndCreditAreUnbalanced()
    {
        String idempotencyKey = "test-unbalanced-001";
        Long driverId = 4L;
        Long paymentId = 400L;

        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("10000"),
                        paymentId,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("9000"),
                        paymentId,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        idempotencyKey,
                        driverId,
                        "PAYMENT",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("차변과 대변의 합이 일치하지 않습니다.");
    }

    /**
     * 동일한 멱등성 키로 요청이 다시 전달될 때 기존 처리 결과를 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("동일한 Idempotency-Key 재요청은 중복 분개를 생성하지 않는다")
    void recordEntriesDoesNotDuplicateEntriesWithSameIdempotencyKey()
    {
        String idempotencyKey = "test-idempotency-001";
        Long driverId = 5L;
        Long paymentId = 500L;

        List<LedgerEntryRequest.EntryDetail> entries = List.of(
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

        Long firstLedgerId = ledgerService.recordEntries(
                idempotencyKey,
                driverId,
                "PAYMENT",
                entries
        );

        Long secondLedgerId = ledgerService.recordEntries(
                idempotencyKey,
                driverId,
                "PAYMENT",
                entries
        );

        assertThat(secondLedgerId)
                .isEqualTo(firstLedgerId);
    }

    /**
     * 멱등성 키가 비어 있는 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("Idempotency-Key가 비어 있으면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenIdempotencyKeyIsBlank()
    {
        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("10000"),
                        600L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000"),
                        600L,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        " ",
                        6L,
                        "PAYMENT",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idempotency-Key는 필수입니다.");
    }

    /**
     * 지원하지 않는 분개 유형이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("지원하지 않는 entryType이면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenEntryTypeIsInvalid()
    {
        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("10000"),
                        700L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000"),
                        700L,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        "test-invalid-type-001",
                        7L,
                        "HELLO",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 entryType입니다.");
    }

    /**
     * 분개 목록이 비어 있는 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("분개 목록이 비어 있으면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenEntriesAreEmpty()
    {
        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        "test-empty-entries-001",
                        8L,
                        "PAYMENT",
                        List.of()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("분개 목록은 비어 있을 수 없습니다.");
    }

    /**
     * 허용되지 않은 분개 방향이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("direction이 DEBIT 또는 CREDIT이 아니면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenDirectionIsInvalid()
    {
        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "ABC",
                        new BigDecimal("10000"),
                        900L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000"),
                        900L,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        "test-invalid-direction-001",
                        9L,
                        "PAYMENT",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("direction은 DEBIT 또는 CREDIT이어야 합니다.");
    }

    /**
     * 금액이 없는 분개 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("분개 금액이 null이면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenAmountIsNull()
    {
        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        null,
                        1000L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000"),
                        1000L,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        "test-null-amount-001",
                        10L,
                        "PAYMENT",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("분개 금액은 필수입니다.");
    }

    /**
     * 정산 분개에 paymentId가 포함된 요청이 거부되는지 확인합니다.
     */
    @Test
    @DisplayName("SETTLEMENT 분개의 paymentId가 존재하면 분개 기록에 실패한다")
    void recordEntriesThrowsExceptionWhenSettlementHasPaymentId()
    {
        List<LedgerEntryRequest.EntryDetail> entries = List.of(
                new LedgerEntryRequest.EntryDetail(
                        "DEBIT",
                        new BigDecimal("10000"),
                        1100L,
                        "DRIVER"
                ),
                new LedgerEntryRequest.EntryDetail(
                        "CREDIT",
                        new BigDecimal("10000"),
                        null,
                        "PLATFORM"
                )
        );

        assertThatThrownBy(
                () -> ledgerService.recordEntries(
                        "test-settlement-payment-id-001",
                        11L,
                        "SETTLEMENT",
                        entries
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SETTLEMENT 분개의 paymentId는 null이어야 합니다.");
    }
}