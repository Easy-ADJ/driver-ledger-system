package com.example.driverledgersystem.controller;

import com.example.driverledgersystem.dto.UnpaidDriverListResponse;
import com.example.driverledgersystem.exception.GlobalExceptionHandler;
import com.example.driverledgersystem.service.LedgerService;
import com.example.driverledgersystem.service.PaymentSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LedgerController의 HTTP 요청 및 응답을 검증하는 단위 테스트입니다.
 */
class LedgerControllerTest
{
    private MockMvc mockMvc;
    private LedgerService ledgerService;
    private PaymentSyncService paymentSyncService;

    /**
     * 각 테스트 실행 전에 MockMvc와 Service Mock을 초기화합니다.
     */
    @BeforeEach
    void setUp()
    {
        ledgerService =
                Mockito.mock(LedgerService.class);

        paymentSyncService =
                Mockito.mock(PaymentSyncService.class);

        LedgerController ledgerController =
                new LedgerController(
                        ledgerService,
                        paymentSyncService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(ledgerController)
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .build();
    }

    /**
     * 정상적인 분개 기록 요청에 대해 201 Created를 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("정상적인 분개 기록 요청은 201 Created를 반환한다")
    void recordEntriesReturnsCreatedWhenRequestIsValid() throws Exception
    {
        when(ledgerService.recordEntries(
                anyString(),
                anyLong(),
                anyString(),
                any()
        ))
                .thenReturn(1L);

        String requestBody = """
                {
                  "driverId": 1,
                  "entryType": "PAYMENT",
                  "entries": [
                    {
                      "direction": "CREDIT",
                      "amount": "15000",
                      "paymentId": 100,
                      "ownerType": "DRIVER"
                    },
                    {
                      "direction": "DEBIT",
                      "amount": "15000",
                      "paymentId": 100,
                      "ownerType": "PLATFORM"
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/ledger/entries")
                                .header(
                                        "Idempotency-Key",
                                        "test-controller-payment-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.ledgerId")
                                .value(1L)
                );
    }

    /**
     * 분개 기록 과정에서 잘못된 요청 예외가 발생한 경우
     * 공통 오류 응답을 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("잘못된 분개 기록 요청은 400과 ErrorResponse를 반환한다")
    void recordEntriesReturnsBadRequestWhenRequestIsInvalid() throws Exception
    {
        when(ledgerService.recordEntries(
                anyString(),
                anyLong(),
                anyString(),
                any()
        ))
                .thenThrow(
                        new IllegalArgumentException(
                                "차변과 대변의 합이 일치하지 않습니다."
                        )
                );

        String requestBody = """
                {
                  "driverId": 1,
                  "entryType": "PAYMENT",
                  "entries": [
                    {
                      "direction": "CREDIT",
                      "amount": "15000",
                      "paymentId": 100,
                      "ownerType": "DRIVER"
                    },
                    {
                      "direction": "DEBIT",
                      "amount": "10000",
                      "paymentId": 100,
                      "ownerType": "PLATFORM"
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/ledger/entries")
                                .header(
                                        "Idempotency-Key",
                                        "test-controller-invalid-001"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "차변과 대변의 합이 일치하지 않습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.transactionId")
                                .doesNotExist()
                );
    }

    /**
     * 결제 서버의 결제 내역 동기화 요청이 정상적으로 처리되는지 확인합니다.
     */
    @Test
    @DisplayName("결제 내역 동기화 요청은 200 OK와 저장 건수를 반환한다")
    void syncPaymentsReturnsOk() throws Exception
    {
        when(paymentSyncService.syncPayments(1L))
                .thenReturn(4);

        mockMvc.perform(
                        post("/api/ledger/sync")
                                .param(
                                        "driver_id",
                                        "1"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.syncedCount")
                                .value(4)
                );
    }

    /**
     * 미지급 기사 목록 조회 요청이 정상적으로 처리되는지 확인합니다.
     */
    @Test
    @DisplayName("미지급 기사 목록 조회는 200 OK를 반환한다")
    void getUnpaidDriversReturnsOk() throws Exception
    {
        LocalDate date =
                LocalDate.of(
                        2026,
                        8,
                        22
                );

        UnpaidDriverListResponse response =
                UnpaidDriverListResponse.builder()
                        .targetDate(
                                date.toString()
                        )
                        .data(
                                List.of()
                        )
                        .build();

        when(ledgerService.getUnpaidBalances(date))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/ledger/unpaid")
                                .param(
                                        "date",
                                        "2026-08-22"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.targetDate")
                                .value("2026-08-22")
                )
                .andExpect(
                        jsonPath("$.data")
                                .isArray()
                );
    }

    /**
     * 기사별 미지급금 및 결제 근거 조회가 정상적으로 처리되는지 확인합니다.
     */
    @Test
    @DisplayName("기사별 원장 조회는 200 OK와 미지급 금액을 반환한다")
    void getDriverLedgerReturnsOk() throws Exception
    {
        Long driverId = 1L;

        when(
                ledgerService
                        .calculateDriverUnpaidBalance(
                                driverId
                        )
        )
                .thenReturn(
                        new BigDecimal("15000")
                );

        when(
                ledgerService.getPaymentDetails(
                        driverId,
                        null,
                        null
                )
        )
                .thenReturn(
                        List.of()
                );

        mockMvc.perform(
                        get("/api/ledger")
                                .param(
                                        "driver_id",
                                        "1"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.driverId")
                                .value(1L)
                )
                .andExpect(
                        jsonPath("$.totalUnpaidAmount")
                                .value("15000")
                )
                .andExpect(
                        jsonPath("$.paymentDetails")
                                .isArray()
                );
    }

    /**
     * 원장 정합성이 정상인 경우 200 OK를 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("원장 정합성이 정상인 경우 verify API는 200 OK를 반환한다")
    void verifyLedgerReturnsOkWhenLedgerIsBalanced() throws Exception
    {
        LocalDate from =
                LocalDate.of(
                        2026,
                        8,
                        21
                );

        LocalDate to =
                LocalDate.of(
                        2026,
                        8,
                        22
                );

        when(
                ledgerService.verifyLedgerIntegrity(
                        from,
                        to
                )
        )
                .thenReturn(true);

        mockMvc.perform(
                        get("/api/ledger/verify")
                                .param(
                                        "from",
                                        "2026-08-21"
                                )
                                .param(
                                        "to",
                                        "2026-08-22"
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        content().string("")
                );
    }

    /**
     * 원장 정합성이 깨진 경우 409 Conflict를 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("원장 정합성이 깨진 경우 verify API는 409 Conflict를 반환한다")
    void verifyLedgerReturnsConflictWhenLedgerIsUnbalanced() throws Exception
    {
        LocalDate from =
                LocalDate.of(
                        2026,
                        8,
                        21
                );

        LocalDate to =
                LocalDate.of(
                        2026,
                        8,
                        22
                );

        when(
                ledgerService.verifyLedgerIntegrity(
                        from,
                        to
                )
        )
                .thenReturn(false);

        mockMvc.perform(
                        get("/api/ledger/verify")
                                .param(
                                        "from",
                                        "2026-08-21"
                                )
                                .param(
                                        "to",
                                        "2026-08-22"
                                )
                )
                .andExpect(
                        status().isConflict()
                );
    }

    /**
     * 잘못된 정합성 조회 기간에 대해 400 Bad Request를 반환하는지 확인합니다.
     */
    @Test
    @DisplayName("from이 to보다 이후이면 verify API는 400 Bad Request를 반환한다")
    void verifyLedgerReturnsBadRequestWhenFromIsAfterTo() throws Exception
    {
        LocalDate from =
                LocalDate.of(
                        2026,
                        8,
                        22
                );

        LocalDate to =
                LocalDate.of(
                        2026,
                        8,
                        21
                );

        when(
                ledgerService.verifyLedgerIntegrity(
                        from,
                        to
                )
        )
                .thenThrow(
                        new IllegalArgumentException(
                                "조회 시작 날짜는 종료 날짜보다 이후일 수 없습니다."
                        )
                );

        mockMvc.perform(
                        get("/api/ledger/verify")
                                .param(
                                        "from",
                                        "2026-08-22"
                                )
                                .param(
                                        "to",
                                        "2026-08-21"
                                )
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "조회 시작 날짜는 종료 날짜보다 이후일 수 없습니다."
                                )
                );
    }
}