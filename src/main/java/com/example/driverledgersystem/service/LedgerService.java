package com.example.driverledgersystem.service;

import com.example.driverledgersystem.entity.LedgerEntry;
import com.example.driverledgersystem.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 원장 시스템의 핵심 비즈니스 로직(분개 기록, 미지급금 산정, 정합성 검증)을 처리
@Service
@RequiredArgsConstructor
public class LedgerService
{
    private final LedgerEntryRepository entryRepository;

    //1. 결제 분개 기록 (POST /api/ledger/entries 호출 시 연동)
    @Transactional
    public void recordPaymentEntry(String idempotencyKey, Long driverId, Long paymentId, BigDecimal amount)
    {

        if (entryRepository.existsByIdempotencyKey(idempotencyKey))
        {
            return;
        }

        // 차변(DEBIT) 분개 생성(예수금)
        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setDriverId(driverId);
        debitEntry.setPaymentId(paymentId);
        debitEntry.setIdempotencyKey(idempotencyKey);
        debitEntry.setEntryType("PAYMENT");
        debitEntry.setDirection("DEBIT");
        debitEntry.setAmount(amount);
        debitEntry.setApprovedAt(LocalDateTime.now());

        // 대변(CREDIT) 분개 생성(기사님 미지급금)
        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setDriverId(driverId);
        creditEntry.setPaymentId(paymentId);
        creditEntry.setIdempotencyKey(idempotencyKey + "_CREDIT"); // 키 중복 방어 분기
        creditEntry.setEntryType("PAYMENT");
        creditEntry.setDirection("CREDIT");
        creditEntry.setAmount(amount);
        creditEntry.setApprovedAt(LocalDateTime.now());

        // 차변 및 대변 합계 정합성 검증
        validateDoubleEntry(debitEntry, creditEntry);

        entryRepository.save(debitEntry);
        entryRepository.save(creditEntry);
    }

    //2. 기사별 미지급금 잔액 계산 (GET /api/ledger?driver_id= 용도)
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId)
    {
        BigDecimal totalCredit = entryRepository.sumAmountByDriverIdAndDirection(driverId, "CREDIT");
        BigDecimal totalDebit = entryRepository.sumAmountByDriverIdAndDirection(driverId, "DEBIT");

        return totalCredit.subtract(totalDebit);
    }

    //차변(DEBIT)및 대변(CREDIT) 금액 총합 일치 여부 확인
    private void validateDoubleEntry(LedgerEntry entry1, LedgerEntry entry2)
    {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        if ("DEBIT".equals(entry1.getDirection())) totalDebit = totalDebit.add(entry1.getAmount());
        if ("CREDIT".equals(entry1.getDirection())) totalCredit = totalCredit.add(entry1.getAmount());

        if ("DEBIT".equals(entry2.getDirection())) totalDebit = totalDebit.add(entry2.getAmount());
        if ("CREDIT".equals(entry2.getDirection())) totalCredit = totalCredit.add(entry2.getAmount());

        if (totalDebit.compareTo(totalCredit) != 0)
        {
            throw new IllegalStateException("차변과 대변의 합계가 일치하지 않습니다.");
        }
    }
}