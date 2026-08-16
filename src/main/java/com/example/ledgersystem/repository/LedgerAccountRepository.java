package com.example.ledgersystem.repository;

import com.example.ledgersystem.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {
    // ownerType과 ownerId로 통장 조회
    Optional<LedgerAccount> findByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}