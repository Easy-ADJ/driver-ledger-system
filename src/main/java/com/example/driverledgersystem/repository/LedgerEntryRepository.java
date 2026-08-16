package com.example.driverledgersystem.repository;

import com.example.driverledgersystem.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;


public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>
{
    //동일한 멱등성 키를 가진 기록이 존재하는지 확인(중복 방어)
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.driverId = :driverId AND e.direction = :direction")
    BigDecimal sumAmountByDriverIdAndDirection(
            @Param("driverId") Long driverId,
            @Param("direction") String direction
    );
}