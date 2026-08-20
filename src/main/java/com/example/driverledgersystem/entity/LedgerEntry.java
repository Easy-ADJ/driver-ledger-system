package com.example.driverledgersystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 원장 분개 내역(거래 기록) 저장 엔티티
@Entity
@Table(name = "LEDGER_ENTRIES")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    // 정산(SETTLEMENT) 지급 분개 시에는 결제 ID가 없으므로 nullable
    @Column(name = "payment_id")
    private Long paymentId;

    // 동시 중복 결제 방지
    @Column(name = "idempotency_key", length = 64, unique = true, nullable = false)
    private String idempotencyKey;

    // PAYMENT, PAYMENT_CANCEL, SETTLEMENT
    @Column(name = "entry_type", length = 20, nullable = false)
    private String entryType;

    // DEBIT(차변/예수금) 또는 CREDIT(대변/미지급금)
    @Column(name = "direction", length = 10, nullable = false)
    private String direction;

    @Column(name = "amount", precision = 12, scale = 0, nullable = false)
    private BigDecimal amount;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}