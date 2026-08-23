package com.example.driverledgersystem.repository;

import com.example.driverledgersystem.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 원장 분개 데이터의 조회 및 집계를 담당하는 Repository입니다.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>
{
    /**
     * 멱등성 키에 해당하는 최초 원장 분개를 조회합니다.
     *
     * @param idempotencyKey 요청의 멱등성 키
     * @return 최초 원장 분개
     */
    Optional<LedgerEntry> findFirstByIdempotencyKeyOrderByLedgerIdAsc(
            String idempotencyKey
    );

    /**
     * 특정 기사의 특정 결제가 이미 원장에 기록되어 있는지 확인합니다.
     *
     * @param driverId  기사 ID
     * @param paymentId 결제 ID
     * @param entryType 분개 유형
     * @return 존재 여부
     */
    boolean existsByDriverIdAndPaymentIdAndEntryType(
            Long driverId,
            Long paymentId,
            String entryType
    );

    /**
     * 지정된 시각까지 기사의 특정 방향 분개 금액 합계를 계산합니다.
     *
     * @param driverId  기사 ID
     * @param direction 분개 방향
     * @param endOfDay  조회 기준 시각
     * @return 분개 금액 합계
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM LedgerEntry e
            WHERE e.driverId = :driverId
              AND e.direction = :direction
              AND e.approvedAt <= :endOfDay
            """)
    BigDecimal sumAmountByDriverIdAndDirectionBefore(
            @Param("driverId") Long driverId,
            @Param("direction") String direction,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * 기사의 특정 분개 유형에 해당하는 원장 내역을 조회합니다.
     *
     * @param driverId  기사 ID
     * @param entryType 분개 유형
     * @return 원장 분개 목록
     */
    List<LedgerEntry> findByDriverIdAndEntryType(
            Long driverId,
            String entryType
    );

    /**
     * 지정된 시각까지 기사의 특정 분개 유형에 해당하는 원장 내역을 조회합니다.
     *
     * @param driverId  기사 ID
     * @param entryType 분개 유형
     * @param endOfDay  조회 기준 시각
     * @return 원장 분개 목록
     */
    List<LedgerEntry> findByDriverIdAndEntryTypeAndApprovedAtBefore(
            Long driverId,
            String entryType,
            LocalDateTime endOfDay
    );

    /**
     * 기사의 여러 분개 유형에 해당하는 원장 내역을 조회합니다.
     *
     * @param driverId   기사 ID
     * @param entryTypes 조회할 분개 유형 목록
     * @return 원장 분개 목록
     */
    List<LedgerEntry> findByDriverIdAndEntryTypeIn(
            Long driverId,
            List<String> entryTypes
    );

    /**
     * 지정된 기간 동안 발생한 기사의 여러 분개 유형을 조회합니다.
     * <p>
     * approvedAt은 startInclusive 이상,
     * endExclusive 미만인 분개만 반환합니다.
     *
     * @param driverId       기사 ID
     * @param entryTypes     조회할 분개 유형 목록
     * @param startInclusive 조회 시작 시각
     * @param endExclusive   조회 종료 시각
     * @return 원장 분개 목록
     */
    List<LedgerEntry>
    findByDriverIdAndEntryTypeInAndApprovedAtGreaterThanEqualAndApprovedAtLessThan(
            Long driverId,
            List<String> entryTypes,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );

    /**
     * 지정된 시각까지 원장 기록이 존재하는 기사 ID 목록을 조회합니다.
     *
     * @param endOfDay 조회 기준 시각
     * @return 기사 ID 목록
     */
    @Query("""
            SELECT DISTINCT e.driverId
            FROM LedgerEntry e
            WHERE e.approvedAt <= :endOfDay
              AND e.driverId IS NOT NULL
            """)
    List<Long> findDistinctDriverIdsBefore(
            @Param("endOfDay") LocalDateTime endOfDay
    );

    /**
     * 지정된 기간의 전체 분개 합계를 계산합니다.
     * CREDIT은 양수, DEBIT은 음수로 계산합니다.
     *
     * @param from 조회 시작 시각
     * @param to   조회 종료 시각
     * @return 기간 내 전체 분개의 합계
     */
    @Query("""
            SELECT COALESCE(
                SUM(
                    CASE
                        WHEN e.direction = 'CREDIT' THEN e.amount
                        WHEN e.direction = 'DEBIT' THEN -e.amount
                        ELSE 0
                    END
                ),
                0
            )
            FROM LedgerEntry e
            WHERE e.approvedAt >= :from
              AND e.approvedAt <= :to
            """)
    BigDecimal sumSignedAmountBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}