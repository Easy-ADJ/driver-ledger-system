package com.example.driverledgersystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 원장 분개 기록 API의 요청 데이터를 담는 DTO입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryRequest
{
    private String idempotencyKey;
    private Long driverId;
    private String entryType;
    private List<EntryDetail> entries;

    /**
     * 원장에 기록할 개별 분개 정보를 담는 DTO입니다.
     */
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
        private String ownerType;
    }
}