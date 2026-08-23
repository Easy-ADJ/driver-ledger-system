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

/**
 * 원장 분개 기록, 기사별 잔액 조회 및 원장 정합성 검증 API를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController
{
    private final LedgerService ledgerService;

    /**
     * 하나의 거래에 대한 원장 분개를 기록합니다.
     *
     * @param idempotencyKey 요청의 멱등성 키
     * @param request        원장 분개 기록 요청
     * @return 생성된 원장 ID
     */
    @PostMapping("/entries")
    public ResponseEntity<Map<String, Long>> recordEntries(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LedgerEntryRequest request
    )
    {
        log.info(
                "POST /api/ledger/entries - Idempotency-Key: {}, driverId: {}",
                idempotencyKey,
                request.getDriverId()
        );

        Long ledgerId = ledgerService.recordEntries(
                idempotencyKey,
                request.getDriverId(),
                request.getEntryType(),
                request.getEntries()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("ledgerId", ledgerId));
    }

    /**
     * 지정된 날짜를 기준으로 미지급 잔액이 존재하는 기사 목록을 조회합니다.
     *
     * @param date 조회 기준 날짜
     * @return 미지급 기사 목록
     */
    @GetMapping("/unpaid")
    public ResponseEntity<UnpaidDriverListResponse> getUnpaidDrivers(
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    )
    {
        log.info(
                "GET /api/ledger/unpaid - date: {}",
                date
        );

        UnpaidDriverListResponse response =
                ledgerService.getUnpaidBalances(date);

        return ResponseEntity.ok(response);
    }

    /**
     * 기사의 현재 미지급 잔액과 결제 근거 내역을 조회합니다.
     *
     * from, to가 모두 지정되면 해당 기간의 결제 근거 내역만 반환합니다.
     * from, to가 모두 없으면 기존과 동일하게 전체 결제 근거 내역을 반환합니다.
     *
     * @param driverId 기사 ID
     * @param from     결제 내역 조회 시작 날짜
     * @param to       결제 내역 조회 종료 날짜
     * @return 기사별 원장 조회 결과
     */
    @GetMapping
    public ResponseEntity<DriverLedgerResponse> getDriverLedger(
            @RequestParam("driver_id") Long driverId,

            @RequestParam(
                    value = "from",
                    required = false
            )
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(
                    value = "to",
                    required = false
            )
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    )
    {
        log.info(
                "GET /api/ledger - driverId: {}, from: {}, to: {}",
                driverId,
                from,
                to
        );

        BigDecimal unpaidBalance =
                ledgerService.calculateDriverUnpaidBalance(driverId);

        List<DriverLedgerResponse.PaymentDetail> paymentDetails =
                ledgerService.getPaymentDetails(
                        driverId,
                        from,
                        to
                );

        DriverLedgerResponse response =
                DriverLedgerResponse.builder()
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
        log.info(
                "GET /api/ledger/verify - from: {}, to: {}",
                from,
                to
        );

        boolean balanced =
                ledgerService.verifyLedgerIntegrity(
                        from,
                        to
                );

        if (!balanced)
        {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .build();
        }

        return ResponseEntity.ok().build();
    }
}