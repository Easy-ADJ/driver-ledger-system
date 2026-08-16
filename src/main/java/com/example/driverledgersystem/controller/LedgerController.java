package com.example.driverledgersystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

//  원장 시스템의 API 요청 처리 컨트롤러
@RestController
@RequestMapping("/api/ledger")
public class LedgerController
{
    // 1. 분개 기록 (POST /api/ledger/entries)
    @PostMapping("/entries")
    public ResponseEntity<Object> recordEntries(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Object requestBody // TODO: LedgerEntryRequest DTO 클래스로 변경 예정
    )
    {
        // TODO: 서비스 계층을 호출하여 분개 기록 로직(차변/대변 합계 검증 및 멱등성 처리) 실행

        // 정상 생성
        return ResponseEntity.status(201).build();
    }

    //2. 미지급 기사 목록 조회 (GET /api/ledger/unpaid?date=)
    @GetMapping("/unpaid")
    public ResponseEntity<Object> getUnpaidDrivers(
            @RequestParam("date") String date
    )
    {
        // TODO: 서비스 계층을 호출하여 미지급 기사 목록 조회

        return ResponseEntity.ok().build();
    }

    // 3. 기사별 미지급금 및 근거 조회 (GET /api/ledger?driver_id=)
    @GetMapping
    public ResponseEntity<Object> getDriverLedger(
            @RequestParam("driver_id") Long driverId
    )
    {
        // TODO: 서비스 계층을 호출하여 기사별 잔액 및 결제 건별 내역 합산

        return ResponseEntity.ok().build();
    }

    // 4. 정합성 검증 (GET /api/ledger/verify?from=&to=)
    @GetMapping("/verify")
    public ResponseEntity<Object> verifyIntegrity(
            @RequestParam("from") String from,
            @RequestParam("to") String to
    )
    {
        // TODO: 서비스 계층을 호출하여 차변과 대변 총합 검증 로직 실행

        return ResponseEntity.ok().build();
    }
}