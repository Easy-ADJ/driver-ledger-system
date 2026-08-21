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

/**
 * 원장의 개별 분개 내역을 저장하는 엔티티입니다.
 * <p>
 * 하나의 거래는 차변(DEBIT)과 대변(CREDIT) 분개로 구성되며,
 * PAYMENT, PAYMENT_CANCEL, SETTLEMENT 유형을 지원합니다.
 */
@Entity
@Table(name = "LEDGER_ENTRIES")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "driver_id", nullable = true)
    private Long driverId;

    @Column(name = "payment_id", nullable = true)
    private Long paymentId;

    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    @Column(name = "entry_type", length = 20, nullable = false)
    private String entryType;

    @Column(name = "direction", length = 10, nullable = false)
    private String direction;

    @Column(name = "amount", precision = 12, scale = 0, nullable = false)
    private BigDecimal amount;

    @CreatedDate
    @Column(name = "approved_at", updatable = false)
    private LocalDateTime approvedAt;
}