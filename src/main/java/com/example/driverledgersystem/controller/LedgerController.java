package com.example.driverledgersystem.controller;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

@Slf4j
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController
{
    private final LedgerService ledgerService;

    @PostMapping("/entries")
    public ResponseEntity<Map<String, Long>> recordEntries(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LedgerEntryRequest request
    )
    {
        log.info("API 호출됨: POST /api/ledger/entries, Idempotency-Key: {}, 기사 ID: {}", idempotencyKey, request.getDriverId());

        Long ledgerId = ledgerService.recordEntries(
                idempotencyKey,
                request.getDriverId(),
                request.getEntryType(),
                request.getEntries()
        );

        return ResponseEntity.status(201).body(Map.of("ledgerId", ledgerId));
    }

    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidDriverListResponse> getUnpaidDrivers(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    )
    {
        log.info("API 호출됨: GET /api/ledger/unpaid, 요청 날짜: {}", date);

        UnpaidDriverListResponse response = ledgerService.getUnpaidBalances(date);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<DriverLedgerResponse> getDriverLedger(
            @RequestParam("driver_id") Long driverId
    )
    {
        log.info("API 호출됨: GET /api/ledger, 기사 ID: {}", driverId);

        BigDecimal unpaidBalance = ledgerService.calculateDriverUnpaidBalance(driverId);
        List<DriverLedgerResponse.PaymentDetail> paymentDetails = ledgerService.getPaymentDetails(driverId);

        DriverLedgerResponse response = DriverLedgerResponse.builder()
                .driverId(driverId)
                .totalUnpaidAmount(unpaidBalance)
                .paymentDetails(paymentDetails)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 지정된 기간의 전체 원장 분개 정합성을 검증합니다.
     *
     * @param from 조회 시작 날짜
     * @param to   조회 종료 날짜
     * @return 정합성 검증 결과에 따른 HTTP 응답
     */
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyLedger(
            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    )
    {
        log.info("GET /api/ledger/verify - from: {}, to: {}", from, to);

        boolean balanced = ledgerService.verifyLedgerIntegrity(from, to);

        if (!balanced)
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.ok().build();
    }
}