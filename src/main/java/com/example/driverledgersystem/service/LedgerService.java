package com.example.driverledgersystem.service;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest.EntryDetail;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.entity.LedgerAccount;
import com.example.driverledgersystem.entity.LedgerEntry;
import com.example.driverledgersystem.repository.LedgerAccountRepository;
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

@Service
@RequiredArgsConstructor
public class LedgerService
{
    private final LedgerEntryRepository entryRepository;
    private final LedgerAccountRepository ledgerAccountRepository;

    private void validateIntegerAmount(BigDecimal amount)
    {
        if (amount != null && amount.stripTrailingZeros().scale() > 0)
        {
            throw new IllegalArgumentException("원장 금액은 1원 단위의 정수여야 합니다.");
        }
    }

    // 정산 지급 상쇄 분개(미지급금을 0으로 차감)
    @Transactional
    public Long recordPayoutEntry(String idempotencyKey, Long driverId, List<EntryDetail> entries)
    {
        return processEntries(idempotencyKey, driverId, "PAYOUT", entries);
    }

    // 결제 분개 기록
    @Transactional
    public Long recordPaymentEntry(String idempotencyKey, Long driverId, String entryType, List<EntryDetail> entries)
    {
        return processEntries(idempotencyKey, driverId, entryType, entries);
    }

    // 차변/대변 리스트 전체를 순회하여 DB에 분개 기록
    private Long processEntries(String idempotencyKey, Long driverId, String entryType, List<EntryDetail> entries)
    {
        Optional<LedgerEntry> existingEntry = entryRepository.findByIdempotencyKey(idempotencyKey);
        if (existingEntry.isPresent())
        {
            return existingEntry.get().getLedgerId();
        }

        // 차변(DEBIT)과 대변(CREDIT) 합계 일치 검증
        validateDoubleEntryList(entries);

        Long firstLedgerId = null;

        // 리스트를 순회하며 각각의 분개(차변/대변)를 기록
        for (EntryDetail detail : entries)
        {
            validateIntegerAmount(detail.getAmount());

            LedgerEntry entry = new LedgerEntry();
            entry.setIdempotencyKey(idempotencyKey);

            // 💡 핵심: 기사 쪽 분개("DRIVER")일 때만 driverId를 세팅하고, 플랫폼 쪽은 null로 둡니다.
            if ("DRIVER".equals(detail.getOwnerType())) {
                entry.setDriverId(driverId);
            } else {
                entry.setDriverId(null);
            }

            entry.setPaymentId(detail.getPaymentId());
            entry.setEntryType(entryType);
            entry.setDirection(detail.getDirection());
            entry.setAmount(detail.getAmount());

            LedgerEntry saved = entryRepository.save(entry);

            if (firstLedgerId == null)
            {
                firstLedgerId = saved.getLedgerId();
            }
        }
        return firstLedgerId;
    }

    // 2. 기사별 미지급 잔액 계산
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId)
    {
        return calculateDriverUnpaidBalance(driverId, LocalDateTime.now());
    }

    // 날짜 기준으로 잔액 계산
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(Long driverId, LocalDateTime endOfDay)
    {
        BigDecimal totalCredit = entryRepository.sumAmountByDriverIdAndDirectionBefore(driverId, "CREDIT", endOfDay);
        BigDecimal totalDebit = entryRepository.sumAmountByDriverIdAndDirectionBefore(driverId, "DEBIT", endOfDay);

        if (totalCredit == null) totalCredit = BigDecimal.ZERO;
        if (totalDebit == null) totalDebit = BigDecimal.ZERO;

        // 💡 수정됨: .abs()를 제거하여 음수가 발생하면 그대로 정산 서버로 넘어가게 합니다.
        return totalCredit.subtract(totalDebit);
    }

    // 3. 결제 상세 내역 조회
    @Transactional(readOnly = true)
    public List<DriverLedgerResponse.PaymentDetail> getPaymentDetails(Long driverId)
    {
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
    public UnpaidDriverListResponse getUnpaidBalances(LocalDate date)
    {
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);
        List<Long> driverIds = entryRepository.findDistinctDriverIdsBefore(endOfDay);

        List<UnpaidDriverListResponse.DriverUnpaidData> dataList = driverIds.stream()
                .map(driverId ->
                        {
                            BigDecimal balance = calculateDriverUnpaidBalance(driverId, endOfDay);

                            List<LedgerEntry> entries = entryRepository.findByDriverIdAndEntryTypeAndCreatedAtBefore(driverId, "PAYMENT", endOfDay);
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
                )
                .filter(data -> data.getTotalUnpaidAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        return UnpaidDriverListResponse.builder()
                .targetDate(date.toString())
                .data(dataList)
                .build();
    }

    // 복수 분개(리스트)에 대한 차변(DEBIT) 및 대변(CREDIT) 금액 총합 일치 여부 확인
    private void validateDoubleEntryList(List<EntryDetail> entries)
    {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (EntryDetail entry : entries)
        {
            if ("DEBIT".equals(entry.getDirection()))
            {
                totalDebit = totalDebit.add(entry.getAmount());
            } else if ("CREDIT".equals(entry.getDirection()))
            {
                totalCredit = totalCredit.add(entry.getAmount());
            }
        }

        if (totalDebit.compareTo(totalCredit) != 0)
        {
            throw new IllegalArgumentException("차변과 대변의 합이 일치하지 않습니다.");
        }
    }

    public Long getAccountId(String ownerType, Long ownerId)
    {
        LedgerAccount account = ledgerAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("해당 조건의 원장 계정을 찾을 수 없습니다. ownerType: " + ownerType + ", ownerId: " + ownerId));

        return account.getAccountId();
    }
}