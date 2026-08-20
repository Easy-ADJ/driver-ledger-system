package com.example.driverledgersystem.controller;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest;
import com.example.driverledgersystem.dto.UnpaidLedgerResponse;
import com.example.driverledgersystem.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 원장 시스템의 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController
{
    private final LedgerService ledgerService;

    // 1. 분개 기록
    @PostMapping("/entries")
    public ResponseEntity<Map<String, Long>> recordEntries(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LedgerEntryRequest request)
    {
        BigDecimal amount = BigDecimal.ZERO;
        Long paymentId = null;

        if (request.getEntries() != null && !request.getEntries().isEmpty())
        {
            amount = request.getEntries().get(0).getAmount();
            paymentId = request.getEntries().get(0).getPaymentId();
        }

        Long ledgerId = ledgerService.recordPaymentEntry(
                idempotencyKey,
                request.getDriverId(),
                paymentId,
                amount
        );

        return ResponseEntity.status(201).body(Map.of("ledgerId", ledgerId));
    }

    // 2. 미지급 기사 목록 조회
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidLedgerResponse> getUnpaidDrivers(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
    {
        UnpaidLedgerResponse response = ledgerService.getUnpaidBalances(date);
        return ResponseEntity.ok(response);
    }

    // 3. 기사별 미지급금 및 근거 조회
    @GetMapping
    public ResponseEntity<DriverLedgerResponse> getDriverLedger(
            @RequestParam("driver_id") Long driverId
    )
    {
        BigDecimal unpaidBalance = ledgerService.calculateDriverUnpaidBalance(driverId);
        List<DriverLedgerResponse.PaymentDetail> paymentDetails = ledgerService.getPaymentDetails(driverId);

        DriverLedgerResponse response = DriverLedgerResponse.builder()
                .driverId(driverId)
                .totalUnpaidAmount(unpaidBalance)
                .paymentDetails(paymentDetails)
                .build();

        return ResponseEntity.ok(response);
    }

    // 4. 정산 대사용 계정 조회 API
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Long>> getAccount(
            @RequestParam("ownerType") String ownerType,
            @RequestParam("ownerId") Long ownerId
    )
    {
        return ResponseEntity.ok(Map.of("accountId", ownerId));
    }
}