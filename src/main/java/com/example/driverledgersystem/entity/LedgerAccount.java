package com.example.driverledgersystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 원장 계정(Ledger Account) 엔티티
 * 잔액은 직접 저장하지 않고, LedgerEntry(분개)의 합으로 계산합니다.
 */
@Entity
@Table(name = "ledger_accounts")
@Getter
@Setter
public class LedgerAccount
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    // 계정 소유자 타입 (예: "DRIVER", "COMPANY" 등)
    @Column(name = "owner_type", nullable = false, length = 20)
    private String ownerType;

    // 계정 소유자 식별자 (예: 기사 ID)
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
}