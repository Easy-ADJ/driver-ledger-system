package com.example.driverledgersystem.service;

import com.example.driverledgersystem.client.PaymentClient;
import com.example.driverledgersystem.dto.PaymentLedgerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 결제 서버에서 결제 내역을 조회하고 원장으로 동기화합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSyncService
{
    private final PaymentClient paymentClient;
    private final LedgerService ledgerService;

    /**
     * 특정 기사의 결제 내역을 결제 서버에서 가져와
     * 아직 원장에 기록되지 않은 결제만 저장합니다.
     *
     * @param driverId 기사 ID
     * @return 새로 저장한 결제 건수
     */
    public int syncPayments(Long driverId)
    {
        if (driverId == null)
        {
            throw new IllegalArgumentException(
                    "driverId는 필수입니다."
            );
        }

        List<PaymentLedgerResponse> payments =
                paymentClient.getPayments(driverId);

        int syncedCount = 0;

        for (PaymentLedgerResponse payment : payments)
        {
            boolean saved =
                    ledgerService.recordImportedPayment(
                            driverId,
                            payment.getPaymentId(),
                            payment.getAmount(),
                            payment.getApprovedAt()
                    );

            if (saved)
            {
                syncedCount++;
            }
        }

        log.info(
                "결제 내역 동기화 완료 - driverId: {}, 조회 건수: {}, 저장 건수: {}",
                driverId,
                payments.size(),
                syncedCount
        );

        return syncedCount;
    }
}