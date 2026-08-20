package com.example.driverledgersystem.repository;

import com.example.driverledgersystem.entity.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 원장 계정(LedgerAccount) DB 접근을 위한 Repository 인터페이스
 */
@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long>
{
    /**
     * ownerType(예: DRIVER)과 ownerId(예: driverId) 조건으로 계정을 조회하는 쿼리 메서드
     */
    Optional<LedgerAccount> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}