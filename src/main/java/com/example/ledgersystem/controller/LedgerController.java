package com.example.ledgersystem.controller;

import com.example.ledgersystem.domain.BalanceStatus;
import com.example.ledgersystem.dto.PaymentRecordRequest;
import com.example.ledgersystem.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController
{
    private final LedgerService ledgerService;

    @PostMapping("/entries")                // 정상 결제
    public ResponseEntity<String> recordPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRecordRequest request)
    {
        ledgerService.recordPayment(
                request.transactionId(),
                request.platformId(),
                request.driverId(),
                request.amount()
        );

        return ResponseEntity.ok("결제 기록이 저장 되었습니다.");
    }

    @PostMapping("/entries/cancel")         // 결제 취소 기록
    public ResponseEntity<String> cancelPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRecordRequest request)
    {
        // 취소 거래를 식별하기 위해 기존 거래 ID에 '-CANCEL' 등을 붙여 고유한 취소 ID로 활용합니다.
        String cancelTransactionId = request.transactionId() + "-CANCEL";

        ledgerService.cancelPayment(
                request.transactionId(),
                cancelTransactionId,
                request.platformId(),
                request.driverId(),
                request.amount()
        );

        return ResponseEntity.ok("결제 취소가 성공적으로 기록 되었습니다.");
    }

    @PostMapping("/payouts")                // 정산액 지급 기록
    public ResponseEntity<String> recordPayout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRecordRequest request)
    {
        ledgerService.recordPayout(
                request.transactionId(),
                request.platformId(),
                request.driverId(),
                request.amount()
        );

        return ResponseEntity.ok("정산 지급 기록이 성공적으로 저장 되었습니다.");
    }

    @GetMapping("/accounts/{id}/balance")   // 잔액 및 상태 조회
    public ResponseEntity<BalanceStatus> getDriverStatus(@PathVariable("id") Long driverId)
    {
        BalanceStatus status = ledgerService.getDriverBalanceStatus(driverId);
        return ResponseEntity.ok(status);
    }
}