package com.example.driverledgersystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

// POST /api/ledger/entries 요청의 본문(Body) 데이터를 담는 DTO 클래스.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryRequest
{
    private String idempotencyKey;
    private Long driverId;
    private String entryType;

    // 차변 및 대변 분개 내역 리스트
    private List<EntryDetail> entries;

    // 개별 분개 내역 DTO 클래스.
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryDetail
    {
        private String direction;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal amount;

        private Long paymentId;
    }
}