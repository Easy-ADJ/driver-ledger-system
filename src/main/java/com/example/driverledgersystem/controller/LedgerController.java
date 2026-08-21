package com.example.driverledgersystem.controller;

import com.example.driverledgersystem.dto.DriverLedgerResponse;
import com.example.driverledgersystem.dto.LedgerEntryRequest;
import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 👉 1. 로그 기능을 사용하기 위한 Import 추가
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
@Slf4j
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {
    private final LedgerService ledgerService;

    // 1. 분개 기록
    @PostMapping("/entries")
    public ResponseEntity<Map<String, Long>> recordEntries(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LedgerEntryRequest request) {
        // 2. 분개 기록 API 호출 로그
        log.info("API 호출됨: POST /api/ledger/entries, Idempotency-Key: {}, 기사 ID: {}", idempotencyKey, request.getDriverId());

        Long ledgerId = ledgerService.recordEntries(
                idempotencyKey,
                request.getDriverId(),
                request.getEntryType(),
                request.getEntries()
        );

        return ResponseEntity.status(201).body(Map.of("ledgerId", ledgerId));
    }

    // 3. 미지급 기사 목록 조회
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidDriverListResponse> getUnpaidDrivers(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        // 👉 4. 미지급 목록 조회 API 호출 로그
        log.info("API 호출됨: GET /api/ledger/unpaid, 요청 날짜: {}", date);

        UnpaidDriverListResponse response = ledgerService.getUnpaidBalances(date);
        return ResponseEntity.ok(response);
    }

    // 5. 기사별 미지급금 및 근거 조회
    @GetMapping
    public ResponseEntity<DriverLedgerResponse> getDriverLedger(
            @RequestParam("driver_id") Long driverId
    ) {
        // 6. 단건 잔액 조회 API 호출 로그
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
}