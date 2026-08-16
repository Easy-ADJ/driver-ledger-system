package com.example.ledgersystem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entries", uniqueConstraints = {
        // 차변(DEBIT) 및 대변(CREDIT)은 거래 하나당 각각 1개씩만 허용하도록 복합 유니크 제약조건 설정
        @UniqueConstraint(columnNames = {"transaction_id", "direction"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionId; // 결제번호(From 결제)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private LedgerAccount account; // 어떤 통장의 무슨 영수증인지 연결 (N:1)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction; // DEBIT(차변)인지 CREDIT(대변)인지

    @Column(nullable = false, precision = 19, scale = 0) // 원화 정수
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 값을 넣기 위한 생성자
    public LedgerEntry(String transactionId, LedgerAccount account, Direction direction, BigDecimal amount) {
        this.transactionId = transactionId;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
    }
}