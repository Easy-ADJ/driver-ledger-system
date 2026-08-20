package com.example.driverledgersystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class UnpaidLedgerResponse
{
    private String targetDate;
    private List<DriverUnpaidData> data;

    @Getter
    @Builder
    public static class DriverUnpaidData
    {
        private Long driverId;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal totalUnpaidAmount;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime lastApprovedAt;
    }
}