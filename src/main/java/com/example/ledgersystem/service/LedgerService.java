package com.example.ledgersystem.service;

import com.example.ledgersystem.domain.BalanceStatus;
import com.example.ledgersystem.domain.Direction;
import com.example.ledgersystem.domain.LedgerAccount;
import com.example.ledgersystem.domain.LedgerEntry;
import com.example.ledgersystem.repository.LedgerAccountRepository;
import com.example.ledgersystem.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class LedgerService
{
    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;

    @Transactional
    public void recordPayment(String transactionId, Long platformId, Long driverId, BigDecimal amount)
    {
        if (entryRepository.existsByTransactionId(transactionId))
        {
            return;
        }

        LedgerAccount platformAccount = getOrCreateAccount("PLATFORM", platformId);
        LedgerAccount driverAccount = getOrCreateAccount("DRIVER", driverId);

        // 정상 결제: 플랫폼(차변), 기사(대변)
        LedgerEntry platformEntry = new LedgerEntry(transactionId, platformAccount, Direction.DEBIT, amount);
        LedgerEntry driverEntry = new LedgerEntry(transactionId, driverAccount, Direction.CREDIT, amount);

        // 정합성 검증
        validateDoubleEntry(platformEntry, driverEntry);

        entryRepository.save(platformEntry);
        entryRepository.save(driverEntry);
    }

    @Transactional
    public void cancelPayment(String originalTransactionId, String cancelTransactionId, Long platformId, Long driverId, BigDecimal amount)
    {
        if (entryRepository.existsByTransactionId(cancelTransactionId))
        {
            return;
        }

        LedgerAccount platformAccount = getOrCreateAccount("PLATFORM", platformId);
        LedgerAccount driverAccount = getOrCreateAccount("DRIVER", driverId);

        // 상쇄 분개: 방향을 반대로 뒤집음 -> 플랫폼(대변), 기사(차변)
        LedgerEntry platformCancelEntry = new LedgerEntry(cancelTransactionId, platformAccount, Direction.CREDIT, amount);
        LedgerEntry driverCancelEntry = new LedgerEntry(cancelTransactionId, driverAccount, Direction.DEBIT, amount);

        // 정합성 검증
        validateDoubleEntry(platformCancelEntry, driverCancelEntry);

        entryRepository.save(platformCancelEntry);
        entryRepository.save(driverCancelEntry);
    }

    @Transactional(readOnly = true)
    public BalanceStatus getDriverBalanceStatus(Long driverId)
    {
        LedgerAccount driverAccount = accountRepository.findByOwnerTypeAndOwnerId("DRIVER", driverId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기사님입니다."));

        BigDecimal totalCredit = entryRepository.sumAmountByAccountIdAndDirection(driverAccount.getId(), Direction.CREDIT);
        BigDecimal totalDebit = entryRepository.sumAmountByAccountIdAndDirection(driverAccount.getId(), Direction.DEBIT);

        BigDecimal balance = totalCredit.subtract(totalDebit);

        if (balance.compareTo(BigDecimal.ZERO) > 0)
        {
            return BalanceStatus.PAYABLE_POSITIVE;
        }
        else if (balance.compareTo(BigDecimal.ZERO) == 0)
        {
            return BalanceStatus.ZERO;
        }
        else
        {
            return BalanceStatus.ERROR_NEGATIVE;
        }
    }

    // 통장 조회 및 없으면 생성하는 메서드
    private LedgerAccount getOrCreateAccount(String ownerType, Long ownerId)
    {
        return accountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> accountRepository.save(new LedgerAccount(ownerType, ownerId)));
    }

    // 차변(DEBIT)과 대변(CREDIT)의 합계가 일치하는지 검증하는 방어 로직
    private void validateDoubleEntry(LedgerEntry entry1, LedgerEntry entry2)
    {
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        if (entry1.getDirection() == Direction.DEBIT) totalDebit = totalDebit.add(entry1.getAmount());
        if (entry1.getDirection() == Direction.CREDIT) totalCredit = totalCredit.add(entry1.getAmount());

        if (entry2.getDirection() == Direction.DEBIT) totalDebit = totalDebit.add(entry2.getAmount());
        if (entry2.getDirection() == Direction.CREDIT) totalCredit = totalCredit.add(entry2.getAmount());

        if (totalDebit.compareTo(totalCredit) != 0)
        {
            throw new IllegalStateException("차변과 대변의 합계가 일치하지 않습니다.");
        }
    }

    @Transactional
    public void recordPayout(String transactionId, Long platformId, Long driverId, BigDecimal amount)
    {
        if (entryRepository.existsByTransactionId(transactionId))
        {
            return;
        }

        LedgerAccount platformAccount = getOrCreateAccount("PLATFORM", platformId);
        LedgerAccount driverAccount = getOrCreateAccount("DRIVER", driverId);

        // 정산 지급: 기사님에게 돈을 줬으므로 미지급금 감소 -> 기사(차변), 플랫폼(대변)
        LedgerEntry platformPayoutEntry = new LedgerEntry(transactionId, platformAccount, Direction.CREDIT, amount);
        LedgerEntry driverPayoutEntry = new LedgerEntry(transactionId, driverAccount, Direction.DEBIT, amount);

        // 정합성 검증
        validateDoubleEntry(platformPayoutEntry, driverPayoutEntry);

        entryRepository.save(platformPayoutEntry);
        entryRepository.save(driverPayoutEntry);
    }
}