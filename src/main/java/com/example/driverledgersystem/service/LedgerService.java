package com.example.driverledgersystem.service;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest.EntryDetail;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
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

/**
 * 원장 분개 기록과 기사별 미지급금 계산을 담당하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {
    private final LedgerEntryRepository entryRepository;

    /**
     * 하나의 거래에 대한 차변과 대변 분개를 원장에 기록합니다.
     * 동일한 멱등성 키로 이미 처리된 거래가 있으면 기존 원장 ID를 반환합니다.
     * @param idempotencyKey 요청의 멱등성 키
     * @param driverId       기사 ID
     * @param entryType      분개 유형
     * @param entries        차변 및 대변 분개 목록
     * @return 최초 생성된 원장 분개의 ID
     */
    @Transactional
    public Long recordEntries(
            String idempotencyKey,
            Long driverId,
            String entryType,
            List<EntryDetail> entries
    ) {
        Optional<LedgerEntry> existingEntry =
                entryRepository.findFirstByIdempotencyKeyOrderByLedgerIdAsc(idempotencyKey);

        if (existingEntry.isPresent()) {
            return existingEntry.get().getLedgerId();
        }

        validateDoubleEntryList(entries);

        Long firstLedgerId = null;

        for (EntryDetail detail : entries) {
            validateIntegerAmount(detail.getAmount());

            LedgerEntry entry = new LedgerEntry();
            entry.setIdempotencyKey(idempotencyKey);

            if ("DRIVER".equals(detail.getOwnerType())) {
                entry.setDriverId(driverId);
            }

            entry.setPaymentId(detail.getPaymentId());
            entry.setEntryType(entryType);
            entry.setDirection(detail.getDirection());
            entry.setAmount(detail.getAmount());

            LedgerEntry savedEntry = entryRepository.save(entry);

            if (firstLedgerId == null) {
                firstLedgerId = savedEntry.getLedgerId();
            }
        }

        return firstLedgerId;
    }

    /**
     * 기사의 현재 미지급 잔액을 계산합니다.
     * @param driverId 기사 ID
     * @return 현재 미지급 잔액
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId) {
        return calculateDriverUnpaidBalance(driverId, LocalDateTime.now());
    }

    /**
     * 지정된 시각까지의 기사 미지급 잔액을 계산합니다.
     * @param driverId 기사 ID
     * @param endOfDay 조회 기준 시각
     * @return 기준 시각까지의 미지급 잔액
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId, LocalDateTime endOfDay) {
        BigDecimal totalCredit =
                entryRepository.sumAmountByDriverIdAndDirectionBefore(driverId, "CREDIT", endOfDay);
        BigDecimal totalDebit =
                entryRepository.sumAmountByDriverIdAndDirectionBefore(driverId, "DEBIT", endOfDay);

        if (totalCredit == null) {
            totalCredit = BigDecimal.ZERO;
        }

        if (totalDebit == null) {
            totalDebit = BigDecimal.ZERO;
        }

        return totalCredit.subtract(totalDebit);
    }

    /**
     * 기사에게 발생한 결제 분개 내역을 조회합니다.
     * @param driverId 기사 ID
     * @return 결제 건별 상세 내역
     */
    @Transactional(readOnly = true)
    public List<DriverLedgerResponse.PaymentDetail> getPaymentDetails(Long driverId) {
        List<LedgerEntry> entries =
                entryRepository.findByDriverIdAndEntryType(driverId, "PAYMENT");

        return entries.stream()
                .map(entry -> DriverLedgerResponse.PaymentDetail.builder()
                        .paymentId(entry.getPaymentId())
                        .amount(entry.getAmount())
                        .approvedAt(entry.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 지정된 날짜까지 미지급 잔액이 존재하는 기사 목록을 조회합니다.
     * @param date 조회 기준 날짜
     * @return 미지급 기사 목록
     */
    @Transactional(readOnly = true)
    public UnpaidDriverListResponse getUnpaidBalances(LocalDate date) {
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<Long> driverIds = entryRepository.findDistinctDriverIdsBefore(endOfDay);

        List<UnpaidDriverListResponse.DriverUnpaidData> dataList = driverIds.stream()
                .map(driverId -> createDriverUnpaidData(driverId, date, endOfDay))
                .filter(data -> data.getTotalUnpaidAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        return UnpaidDriverListResponse.builder()
                .targetDate(date.toString())
                .data(dataList)
                .build();
    }

    /**
     * 기사 한 명의 미지급금 조회 결과를 생성합니다.
     * @param driverId 기사 ID
     * @param date     조회 기준 날짜
     * @param endOfDay 조회 기준 시각
     * @return 기사 미지급금 정보
     */
    private UnpaidDriverListResponse.DriverUnpaidData createDriverUnpaidData(
            Long driverId,
            LocalDate date,
            LocalDateTime endOfDay
    ) {
        BigDecimal balance = calculateDriverUnpaidBalance(driverId, endOfDay);

        List<LedgerEntry> entries =
                entryRepository.findByDriverIdAndEntryTypeAndCreatedAtBefore(
                        driverId,
                        "PAYMENT",
                        endOfDay
                );

        LocalDateTime lastApprovedAt = entries.stream()
                .map(LedgerEntry::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(date.atStartOfDay());

        return UnpaidDriverListResponse.DriverUnpaidData.builder()
                .driverId(driverId)
                .totalUnpaidAmount(balance)
                .lastApprovedAt(lastApprovedAt)
                .build();
    }

    /**
     * 원장 금액이 1원 단위의 정수인지 검증합니다.
     * @param amount 검증할 금액
     */
    private void validateIntegerAmount(BigDecimal amount) {
        if (amount != null && amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("원장 금액은 1원 단위의 정수여야 합니다.");
        }
    }

    /**
     * 하나의 거래에서 차변과 대변의 합계가 일치하는지 검증합니다.
     * @param entries 검증할 분개 목록
     */
    private void validateDoubleEntryList(List<EntryDetail> entries) {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (EntryDetail entry : entries) {
            if ("DEBIT".equals(entry.getDirection())) {
                totalDebit = totalDebit.add(entry.getAmount());
            } else if ("CREDIT".equals(entry.getDirection())) {
                totalCredit = totalCredit.add(entry.getAmount());
            }
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("차변과 대변의 합이 일치하지 않습니다.");
        }
    }
}