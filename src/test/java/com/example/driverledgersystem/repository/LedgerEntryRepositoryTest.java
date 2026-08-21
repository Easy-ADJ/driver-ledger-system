package com.example.driverledgersystem.repository;

import com.example.driverledgersystem.domain.Direction;
import com.example.driverledgersystem.domain.EntryType;
import com.example.driverledgersystem.entity.LedgerEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LedgerEntryRepository의 원장 조회 및 집계 쿼리를 검증하는 통합 테스트입니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerEntryRepositoryTest
{
    @Autowired
    private LedgerEntryRepository entryRepository;

    /**
     * 기사별 CREDIT 및 DEBIT 금액이 지정된 시각까지
     * 정상적으로 집계되는지 확인합니다.
     */
    @Test
    @DisplayName("기사별 direction 금액 합계가 정상적으로 계산된다")
    void sumAmountByDriverIdAndDirectionBeforeReturnsCorrectAmount()
    {
        Long driverId = 101L;

        entryRepository.save(
                createEntry(
                        "repository-sum-credit-001",
                        driverId,
                        10001L,
                        EntryType.PAYMENT,
                        Direction.CREDIT,
                        new BigDecimal("15000")
                )
        );

        entryRepository.save(
                createEntry(
                        "repository-sum-debit-001",
                        driverId,
                        10002L,
                        EntryType.PAYMENT_CANCEL,
                        Direction.DEBIT,
                        new BigDecimal("5000")
                )
        );

        entryRepository.flush();

        LocalDateTime endOfDay =
                LocalDateTime.now().plusMinutes(1);

        BigDecimal totalCredit =
                entryRepository.sumAmountByDriverIdAndDirectionBefore(
                        driverId,
                        Direction.CREDIT.name(),
                        endOfDay
                );

        BigDecimal totalDebit =
                entryRepository.sumAmountByDriverIdAndDirectionBefore(
                        driverId,
                        Direction.DEBIT.name(),
                        endOfDay
                );

        assertThat(totalCredit)
                .isEqualByComparingTo(new BigDecimal("15000"));

        assertThat(totalDebit)
                .isEqualByComparingTo(new BigDecimal("5000"));
    }

    /**
     * 기사 ID가 없는 PLATFORM 분개가 기사 ID 목록에
     * 포함되지 않는지 확인합니다.
     */
    @Test
    @DisplayName("driverId가 null인 분개는 기사 목록 조회에서 제외된다")
    void findDistinctDriverIdsBeforeExcludesNullDriverId()
    {
        Long driverId = 102L;

        entryRepository.save(
                createEntry(
                        "repository-driver-001",
                        driverId,
                        11001L,
                        EntryType.PAYMENT,
                        Direction.CREDIT,
                        new BigDecimal("12000")
                )
        );

        entryRepository.save(
                createEntry(
                        "repository-platform-001",
                        null,
                        11001L,
                        EntryType.PAYMENT,
                        Direction.DEBIT,
                        new BigDecimal("12000")
                )
        );

        entryRepository.flush();

        LocalDateTime endOfDay =
                LocalDateTime.now().plusMinutes(1);

        List<Long> driverIds =
                entryRepository.findDistinctDriverIdsBefore(endOfDay);

        assertThat(driverIds)
                .contains(driverId);

        assertThat(driverIds)
                .doesNotContainNull();
    }

    /**
     * CREDIT은 양수, DEBIT은 음수로 계산하여
     * 기간 내 signed sum이 정상적으로 계산되는지 확인합니다.
     */
    @Test
    @DisplayName("기간 내 CREDIT과 DEBIT의 signed sum이 정상적으로 계산된다")
    void sumSignedAmountBetweenReturnsCorrectDifference()
    {
        entryRepository.save(
                createEntry(
                        "repository-signed-credit-001",
                        103L,
                        12001L,
                        EntryType.PAYMENT,
                        Direction.CREDIT,
                        new BigDecimal("15000")
                )
        );

        entryRepository.save(
                createEntry(
                        "repository-signed-debit-001",
                        103L,
                        12002L,
                        EntryType.PAYMENT_CANCEL,
                        Direction.DEBIT,
                        new BigDecimal("5000")
                )
        );

        entryRepository.flush();

        LocalDateTime from =
                LocalDateTime.now().minusMinutes(1);

        LocalDateTime to =
                LocalDateTime.now().plusMinutes(1);

        BigDecimal difference =
                entryRepository.sumSignedAmountBetween(
                        from,
                        to
                );

        assertThat(difference)
                .isEqualByComparingTo(new BigDecimal("10000"));
    }

    /**
     * Repository 테스트에 사용할 원장 분개 엔티티를 생성합니다.
     *
     * @param idempotencyKey 멱등성 키
     * @param driverId       기사 ID
     * @param paymentId      결제 ID
     * @param entryType      분개 유형
     * @param direction      분개 방향
     * @param amount         금액
     * @return 생성된 원장 분개
     */
    private LedgerEntry createEntry(
            String idempotencyKey,
            Long driverId,
            Long paymentId,
            EntryType entryType,
            Direction direction,
            BigDecimal amount
    )
    {
        LedgerEntry entry = new LedgerEntry();

        entry.setIdempotencyKey(idempotencyKey);
        entry.setDriverId(driverId);
        entry.setPaymentId(paymentId);
        entry.setEntryType(entryType.name());
        entry.setDirection(direction.name());
        entry.setAmount(amount);

        return entry;
    }
}