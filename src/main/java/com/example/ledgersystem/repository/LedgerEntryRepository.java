package com.example.ledgersystem.repository;

import com.example.ledgersystem.domain.Direction;
import com.example.ledgersystem.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // 결제 기록 확인
    boolean existsByTransactionId(String transactionId);

    // 잔액 조회
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.account.id = :accountId AND e.direction = :direction")
    BigDecimal sumAmountByAccountIdAndDirection(@Param("accountId") Long accountId, @Param("direction") Direction direction);
}