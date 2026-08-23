package com.example.driverledgersystem.service;

import com.example.driverledgersystem.domain.Direction;
import com.example.driverledgersystem.domain.EntryType;
import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest.EntryDetail;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.entity.LedgerEntry;
import com.example.driverledgersystem.exception.InvalidAmountException;
import com.example.driverledgersystem.exception.InvalidDateRangeException;
import com.example.driverledgersystem.exception.InvalidDirectionException;
import com.example.driverledgersystem.exception.InvalidEntryTypeException;
import com.example.driverledgersystem.exception.InvalidLedgerRequestException;
import com.example.driverledgersystem.exception.InvalidSettlementEntryException;
import com.example.driverledgersystem.exception.UnbalancedEntryException;
import com.example.driverledgersystem.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService
{
    private final LedgerEntryRepository entryRepository;

    /**
     * 하나의 거래에 대한 차변과 대변 분개를 원장에 기록합니다.
     * 동일한 멱등성 키로 이미 처리된 거래가 있으면 기존 원장 ID를 반환합니다.
     *
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
    )
    {
        validateRequest(
                idempotencyKey,
                driverId,
                entryType,
                entries
        );

        Optional<LedgerEntry> existingEntry =
                entryRepository.findFirstByIdempotencyKeyOrderByLedgerIdAsc(
                        idempotencyKey
                );

        if (existingEntry.isPresent())
        {
            return existingEntry.get().getLedgerId();
        }

        validateDoubleEntryList(entries);

        Long firstLedgerId = null;

        for (EntryDetail detail : entries)
        {
            LedgerEntry entry = new LedgerEntry();

            entry.setIdempotencyKey(
                    idempotencyKey
            );

            if ("DRIVER".equals(detail.getOwnerType()))
            {
                entry.setDriverId(
                        driverId
                );
            }

            entry.setPaymentId(
                    detail.getPaymentId()
            );

            entry.setEntryType(
                    entryType
            );

            entry.setDirection(
                    detail.getDirection()
            );

            entry.setAmount(
                    detail.getAmount()
            );

            LedgerEntry savedEntry =
                    entryRepository.save(
                            entry
                    );

            if (firstLedgerId == null)
            {
                firstLedgerId =
                        savedEntry.getLedgerId();
            }
        }

        return firstLedgerId;
    }

    /**
     * 결제 서버에서 가져온 결제 데이터를 PAYMENT 분개로 저장합니다.
     * <p>
     * 동일 driverId, paymentId의 PAYMENT 분개가 이미 존재하면
     * 다시 저장하지 않습니다.
     *
     * @param driverId   기사 ID
     * @param paymentId  결제 ID
     * @param amount     결제 금액
     * @param approvedAt 결제 승인 시각
     * @return 새로 저장했으면 true, 기존 결제면 false
     */
    @Transactional
    public boolean recordImportedPayment(
            Long driverId,
            Long paymentId,
            BigDecimal amount,
            LocalDateTime approvedAt
    )
    {
        if (driverId == null)
        {
            throw InvalidLedgerRequestException
                    .missingDriverId();
        }

        if (paymentId == null)
        {
            throw InvalidLedgerRequestException
                    .missingPaymentId();
        }

        validateAmount(amount);

        if (approvedAt == null)
        {
            throw InvalidLedgerRequestException
                    .missingApprovedAt();
        }

        boolean exists =
                entryRepository
                        .existsByDriverIdAndPaymentIdAndEntryType(
                                driverId,
                                paymentId,
                                EntryType.PAYMENT.name()
                        );

        if (exists)
        {
            log.info(
                    "이미 원장에 존재하는 결제입니다. - driverId: {}, paymentId: {}",
                    driverId,
                    paymentId
            );

            return false;
        }

        String idempotencyKey =
                "payment-import-" + paymentId;

        LedgerEntry platformEntry =
                new LedgerEntry();

        platformEntry.setIdempotencyKey(
                idempotencyKey
        );

        platformEntry.setPaymentId(
                paymentId
        );

        platformEntry.setEntryType(
                EntryType.PAYMENT.name()
        );

        platformEntry.setDirection(
                Direction.DEBIT.name()
        );

        platformEntry.setAmount(
                amount
        );

        platformEntry.setApprovedAt(
                approvedAt
        );

        LedgerEntry driverEntry =
                new LedgerEntry();

        driverEntry.setIdempotencyKey(
                idempotencyKey
        );

        driverEntry.setDriverId(
                driverId
        );

        driverEntry.setPaymentId(
                paymentId
        );

        driverEntry.setEntryType(
                EntryType.PAYMENT.name()
        );

        driverEntry.setDirection(
                Direction.CREDIT.name()
        );

        driverEntry.setAmount(
                amount
        );

        driverEntry.setApprovedAt(
                approvedAt
        );

        entryRepository.save(
                platformEntry
        );

        entryRepository.save(
                driverEntry
        );

        log.info(
                "결제 서버 데이터 원장 저장 완료 - driverId: {}, paymentId: {}, amount: {}",
                driverId,
                paymentId,
                amount
        );

        return true;
    }

    /**
     * 기사의 현재 미지급 잔액을 계산합니다.
     *
     * @param driverId 기사 ID
     * @return 현재 미지급 잔액
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(
            Long driverId
    )
    {
        return calculateDriverUnpaidBalance(
                driverId,
                LocalDateTime.now()
        );
    }

    /**
     * 지정된 시각까지의 기사 미지급 잔액을 계산합니다.
     *
     * @param driverId 기사 ID
     * @param endOfDay 조회 기준 시각
     * @return 기준 시각까지의 미지급 잔액
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateDriverUnpaidBalance(
            Long driverId,
            LocalDateTime endOfDay
    )
    {
        BigDecimal totalCredit =
                entryRepository.sumAmountByDriverIdAndDirectionBefore(
                        driverId,
                        Direction.CREDIT.name(),
                        endOfDay
                );

        BigDecimal totalDebit =
                entryRepository.sumAmountByDriverIdAndDirectionBefore(
                        driverId,
                        Direction.DEBIT.name(),
                        endOfDay
                );

        if (totalCredit == null)
        {
            totalCredit =
                    BigDecimal.ZERO;
        }

        if (totalDebit == null)
        {
            totalDebit =
                    BigDecimal.ZERO;
        }

        return totalCredit.subtract(
                totalDebit
        );
    }

    /**
     * 기사에게 발생한 전체 결제 분개 내역을 조회합니다.
     *
     * @param driverId 기사 ID
     * @return 결제 건별 상세 내역
     */
    @Transactional(readOnly = true)
    public List<DriverLedgerResponse.PaymentDetail> getPaymentDetails(
            Long driverId
    )
    {
        return getPaymentDetails(
                driverId,
                null,
                null
        );
    }

    /**
     * 기사에게 발생한 결제 분개 내역을 조회합니다.
     * <p>
     * from, to가 모두 null이면 전체 결제 내역을 조회합니다.
     * from, to가 모두 지정되면 해당 기간의 결제 내역만 조회합니다.
     * <p>
     * 조회 기간은 from의 00:00:00 이상,
     * to 다음 날의 00:00:00 미만으로 처리합니다.
     *
     * @param driverId 기사 ID
     * @param from     조회 시작 날짜
     * @param to       조회 종료 날짜
     * @return 결제 건별 상세 내역
     */
    @Transactional(readOnly = true)
    public List<DriverLedgerResponse.PaymentDetail> getPaymentDetails(
            Long driverId,
            LocalDate from,
            LocalDate to
    )
    {
        validatePaymentDetailPeriod(
                from,
                to
        );

        List<String> paymentEntryTypes =
                List.of(
                        EntryType.PAYMENT.name(),
                        EntryType.PAYMENT_CANCEL.name()
                );

        List<LedgerEntry> entries;

        if (from == null)
        {
            entries =
                    entryRepository.findByDriverIdAndEntryTypeIn(
                            driverId,
                            paymentEntryTypes
                    );
        } else
        {
            LocalDateTime startInclusive =
                    from.atStartOfDay();

            LocalDateTime endExclusive =
                    to.plusDays(1)
                            .atStartOfDay();

            entries =
                    entryRepository
                            .findByDriverIdAndEntryTypeInAndApprovedAtGreaterThanEqualAndApprovedAtLessThan(
                                    driverId,
                                    paymentEntryTypes,
                                    startInclusive,
                                    endExclusive
                            );
        }

        return entries.stream()
                .map(entry ->
                        DriverLedgerResponse.PaymentDetail.builder()
                                .paymentId(
                                        entry.getPaymentId()
                                )
                                .amount(
                                        entry.getAmount()
                                )
                                .approvedAt(
                                        entry.getApprovedAt()
                                )
                                .entryType(
                                        entry.getEntryType()
                                )
                                .build()
                )
                .toList();
    }

    /**
     * 결제 내역 조회 기간 파라미터를 검증합니다.
     * <p>
     * from과 to는 둘 다 존재하거나 둘 다 없어야 합니다.
     *
     * @param from 조회 시작 날짜
     * @param to   조회 종료 날짜
     */
    private void validatePaymentDetailPeriod(
            LocalDate from,
            LocalDate to
    )
    {
        if ((from == null) != (to == null))
        {
            throw InvalidDateRangeException
                    .missingPair();
        }

        if (from != null && from.isAfter(to))
        {
            throw InvalidDateRangeException
                    .reversed();
        }
    }

    /**
     * 지정된 날짜까지 미지급 잔액이 존재하는 기사 목록을 조회합니다.
     *
     * @param date 조회 기준 날짜
     * @return 미지급 기사 목록
     */
    @Transactional(readOnly = true)
    public UnpaidDriverListResponse getUnpaidBalances(
            LocalDate date
    )
    {
        LocalDateTime endOfDay =
                date.atTime(
                        LocalTime.MAX
                );

        List<Long> driverIds =
                entryRepository.findDistinctDriverIdsBefore(
                        endOfDay
                );

        List<UnpaidDriverListResponse.DriverUnpaidData> dataList =
                driverIds.stream()
                        .map(driverId ->
                                createDriverUnpaidData(
                                        driverId,
                                        date,
                                        endOfDay
                                )
                        )
                        .filter(data ->
                                data.getTotalUnpaidAmount()
                                        .compareTo(
                                                BigDecimal.ZERO
                                        ) > 0
                        )
                        .toList();

        return UnpaidDriverListResponse.builder()
                .targetDate(
                        date.toString()
                )
                .data(
                        dataList
                )
                .build();
    }

    /**
     * 지정된 기간의 전체 분개 정합성을 검증합니다.
     *
     * @param from 조회 시작 날짜
     * @param to   조회 종료 날짜
     * @return 원장 정합성이 정상인지 여부
     */
    @Transactional(readOnly = true)
    public boolean verifyLedgerIntegrity(
            LocalDate from,
            LocalDate to
    )
    {
        if (from.isAfter(to))
        {
            throw InvalidDateRangeException
                    .reversed();
        }

        LocalDateTime startOfDay =
                from.atStartOfDay();

        LocalDateTime endOfDay =
                to.atTime(
                        LocalTime.MAX
                );

        BigDecimal difference =
                entryRepository.sumSignedAmountBetween(
                        startOfDay,
                        endOfDay
                );

        boolean balanced =
                difference.compareTo(
                        BigDecimal.ZERO
                ) == 0;

        if (!balanced)
        {
            log.error(
                    "원장 정합성 이상 - from: {}, to: {}, difference: {}",
                    from,
                    to,
                    difference
            );
        }

        return balanced;
    }

    /**
     * 기사 한 명의 미지급금 조회 결과를 생성합니다.
     *
     * @param driverId 기사 ID
     * @param date     조회 기준 날짜
     * @param endOfDay 조회 기준 시각
     * @return 기사 미지급금 정보
     */
    private UnpaidDriverListResponse.DriverUnpaidData createDriverUnpaidData(
            Long driverId,
            LocalDate date,
            LocalDateTime endOfDay
    )
    {
        BigDecimal balance =
                calculateDriverUnpaidBalance(
                        driverId,
                        endOfDay
                );

        List<LedgerEntry> entries =
                entryRepository
                        .findByDriverIdAndEntryTypeAndApprovedAtBefore(
                                driverId,
                                EntryType.PAYMENT.name(),
                                endOfDay
                        );

        LocalDateTime lastApprovedAt =
                entries.stream()
                        .map(
                                LedgerEntry::getApprovedAt
                        )
                        .max(
                                LocalDateTime::compareTo
                        )
                        .orElse(
                                date.atStartOfDay()
                        );

        return UnpaidDriverListResponse.DriverUnpaidData.builder()
                .driverId(
                        driverId
                )
                .totalUnpaidAmount(
                        balance
                )
                .lastApprovedAt(
                        lastApprovedAt
                )
                .build();
    }

    /**
     * 원장 분개 요청의 필수 값을 검증합니다.
     *
     * @param idempotencyKey 요청의 멱등성 키
     * @param driverId       기사 ID
     * @param entryType      분개 유형
     * @param entries        분개 목록
     */
    private void validateRequest(
            String idempotencyKey,
            Long driverId,
            String entryType,
            List<EntryDetail> entries
    )
    {
        if (idempotencyKey == null
                || idempotencyKey.isBlank())
        {
            throw InvalidLedgerRequestException
                    .missingIdempotencyKey();
        }

        if (driverId == null)
        {
            throw InvalidLedgerRequestException
                    .missingDriverId();
        }

        validateEntryType(
                entryType
        );

        if (entries == null
                || entries.isEmpty())
        {
            throw InvalidLedgerRequestException
                    .emptyEntries();
        }

        for (EntryDetail entry : entries)
        {
            validateEntry(
                    entryType,
                    entry
            );
        }
    }

    /**
     * 분개 유형이 허용된 값인지 검증합니다.
     *
     * @param entryType 분개 유형
     */
    private void validateEntryType(
            String entryType
    )
    {
        if (entryType == null)
        {
            throw new InvalidEntryTypeException();
        }

        try
        {
            EntryType.valueOf(
                    entryType
            );
        } catch (IllegalArgumentException e)
        {
            throw new InvalidEntryTypeException();
        }
    }

    /**
     * 개별 분개의 방향, 금액 및 결제 ID 규칙을 검증합니다.
     *
     * @param entryType 분개 유형
     * @param entry     개별 분개
     */
    private void validateEntry(
            String entryType,
            EntryDetail entry
    )
    {
        if (entry == null)
        {
            throw InvalidLedgerRequestException
                    .nullEntry();
        }

        validateDirection(
                entry.getDirection()
        );

        validateAmount(
                entry.getAmount()
        );

        if (EntryType.SETTLEMENT.name().equals(entryType)
                && entry.getPaymentId() != null)
        {
            throw new InvalidSettlementEntryException();
        }
    }

    /**
     * 분개 방향이 허용된 값인지 검증합니다.
     *
     * @param direction 분개 방향
     */
    private void validateDirection(
            String direction
    )
    {
        if (direction == null)
        {
            throw new InvalidDirectionException();
        }

        try
        {
            Direction.valueOf(
                    direction
            );
        } catch (IllegalArgumentException e)
        {
            throw new InvalidDirectionException();
        }
    }

    /**
     * 원장 금액이 유효한 원화 정수인지 검증합니다.
     *
     * @param amount 검증할 금액
     */
    private void validateAmount(
            BigDecimal amount
    )
    {
        if (amount == null)
        {
            throw new InvalidAmountException(
                    "분개 금액은 필수입니다."
            );
        }

        if (amount.signum() <= 0)
        {
            throw new InvalidAmountException(
                    "분개 금액은 0보다 커야 합니다."
            );
        }

        if (amount.stripTrailingZeros()
                .scale() > 0)
        {
            throw new InvalidAmountException(
                    "원장 금액은 1원 단위의 정수여야 합니다."
            );
        }
    }

    /**
     * 하나의 거래에서 차변과 대변의 합계가 일치하는지 검증합니다.
     *
     * @param entries 검증할 분개 목록
     */
    private void validateDoubleEntryList(
            List<EntryDetail> entries
    )
    {
        BigDecimal totalDebit =
                BigDecimal.ZERO;

        BigDecimal totalCredit =
                BigDecimal.ZERO;

        for (EntryDetail entry : entries)
        {
            if (Direction.DEBIT.name().equals(
                    entry.getDirection()
            ))
            {
                totalDebit =
                        totalDebit.add(
                                entry.getAmount()
                        );
            } else
            {
                totalCredit =
                        totalCredit.add(
                                entry.getAmount()
                        );
            }
        }

        if (totalDebit.compareTo(
                totalCredit
        ) != 0)
        {
            throw new UnbalancedEntryException();
        }
    }
}