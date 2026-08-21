package com.example.driverledgersystem.repository;

import com.example.driverledgersystem.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>
{

    Optional<LedgerEntry> findFirstByIdempotencyKeyOrderByLedgerIdAsc(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.driverId = :driverId AND e.direction = :direction")
    BigDecimal sumAmountByDriverIdAndDirection(
            @Param("driverId") Long driverId,
            @Param("direction") String direction
    );

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.driverId = :driverId AND e.direction = :direction AND e.approvedAt <= :endOfDay")
    BigDecimal sumAmountByDriverIdAndDirectionBefore(
            @Param("driverId") Long driverId,
            @Param("direction") String direction,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    // 기사별 결제 건별 상세 목록 조회
    List<LedgerEntry> findByDriverIdAndEntryType(Long driverId, String entryType);

    List<LedgerEntry> findByDriverIdAndEntryTypeAndApprovedAtBefore(Long driverId, String entryType, LocalDateTime endOfDay);

    @Query("SELECT DISTINCT e.driverId FROM LedgerEntry e WHERE e.approvedAt <= :endOfDay AND e.driverId IS NOT NULL")
    List<Long> findDistinctDriverIdsBefore(@Param("endOfDay") LocalDateTime endOfDay);

    /**
     * 지정된 기간의 전체 분개 합계를 계산합니다.
     *
     * CREDIT은 양수, DEBIT은 음수로 계산합니다.
     *
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
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