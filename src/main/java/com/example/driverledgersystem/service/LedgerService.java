package com.example.driverledgersystem.service;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.UnpaidLedgerResponse;
import com.example.driverledgersystem.entity.LedgerEntry;
import com.example.driverledgersystem.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// 원장 시스템 핵심 로직
@Service
@RequiredArgsConstructor
public class LedgerService
{
    private final LedgerEntryRepository entryRepository;

    private void validateIntegerAmount(BigDecimal amount)
    {
        if (amount != null && amount.stripTrailingZeros().scale() > 0)
        {
            throw new IllegalArgumentException("원장 금액은 1원 단위의 정수여야 합니다.");
        }
    }

    // 1. 결제 분개 기록
    @Transactional
    public Long recordPaymentEntry(String idempotencyKey, Long driverId, Long paymentId, BigDecimal amount) {
        // 원화 단위 검증(정수 형태)
        validateIntegerAmount(amount);

        // 멱등성 처리: 존재하는 키면 저장된 ledgerId 반환
        Optional<LedgerEntry> existingEntry = entryRepository.findByIdempotencyKey(idempotencyKey);
        if (existingEntry.isPresent()) {
            return existingEntry.get().getLedgerId();
        }

        LedgerEntry entry = new LedgerEntry();
        entry.setIdempotencyKey(idempotencyKey);
        entry.setDriverId(driverId);
        entry.setPaymentId(paymentId);
        entry.setEntryType("PAYMENT");
        entry.setDirection("CREDIT");
        entry.setAmount(amount);

        LedgerEntry saved = entryRepository.save(entry);
        return saved.getLedgerId();
    }

    // 2. 기사별 미지급 잔액 계산
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId) {
        // 날짜 파라미터가 없는 경우 현재 시간 기준으로 계산
        return calculateDriverUnpaidBalance(driverId, LocalDateTime.now());
    }

    // 날짜 기준으로 잔액 계산
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId, LocalDateTime endOfDay) {
        BigDecimal totalCredit = entryRepository.sumAmountByDriverIdAndDirectionBefore(driverId, "CREDIT", endOfDay);
        BigDecimal totalDebit = entryRepository.sumAmountByDriverIdAndDirectionBefore(driverId, "DEBIT", endOfDay);

        return totalCredit.subtract(totalDebit).abs();  // 양수 반환을 위한 절대값 처리
    }

    // 3. 결제 상세 내역 조회
    @Transactional(readOnly = true)
    public List<DriverLedgerResponse.PaymentDetail> getPaymentDetails(Long driverId) {
        List<LedgerEntry> entries = entryRepository.findByDriverIdAndEntryType(driverId, "PAYMENT");

        return entries.stream()
                .map(entry -> DriverLedgerResponse.PaymentDetail.builder()
                        .paymentId(entry.getPaymentId())
                        .amount(entry.getAmount())
                        .approvedAt(entry.getCreatedAt())
                        .build())
                .toList();
    }

    // 4. 미지급 기사 목록 및 잔액 조회
    @Transactional(readOnly = true)
    public UnpaidLedgerResponse getUnpaidBalances(LocalDate date) {
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<Long> driverIds = entryRepository.findDistinctDriverIdsBefore(endOfDay);

        List<UnpaidLedgerResponse.DriverUnpaidData> dataList = driverIds.stream()
                .map(driverId ->
                        {
                            BigDecimal balance = calculateDriverUnpaidBalance(driverId, endOfDay);

                            List<LedgerEntry> entries = entryRepository.findByDriverIdAndEntryTypeAndCreatedAtBefore(driverId, "PAYMENT", endOfDay);
                            LocalDateTime lastApprovedAt = entries.stream()
                                    .map(LedgerEntry::getCreatedAt)
                                    .max(LocalDateTime::compareTo)
                                    .orElse(date.atStartOfDay());

                            return UnpaidLedgerResponse.DriverUnpaidData.builder()
                                    .driverId(driverId)
                                    .totalUnpaidAmount(balance)
                                    .lastApprovedAt(lastApprovedAt)
                                    .build();
                        }
                )
                .filter(data -> data.getTotalUnpaidAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        return UnpaidLedgerResponse.builder()
                .targetDate(date.toString())
                .data(dataList)
                .build();
    }

    // 차변(DEBIT) 및 대변(CREDIT) 금액 총합 일치 여부 확인
    private void validateDoubleEntry(LedgerEntry entry1, LedgerEntry entry2) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        if ("DEBIT".equals(entry1.getDirection())) {
            totalDebit = totalDebit.add(entry1.getAmount());
        }
        if ("CREDIT".equals(entry1.getDirection())) {
            totalCredit = totalCredit.add(entry1.getAmount());
        }

        if ("DEBIT".equals(entry2.getDirection())) {
            totalDebit = totalDebit.add(entry2.getAmount());
        }
        if ("CREDIT".equals(entry2.getDirection())) {
            totalCredit = totalCredit.add(entry2.getAmount());
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("차변과 대변의 합이 일치하지 않습니다.");
        }
    }
}