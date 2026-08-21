package com.example.driverledgersystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기준 날짜의 미지급 기사 목록 조회 응답을 담는 DTO입니다.
 */
@Getter
@Builder
public class UnpaidDriverListResponse
{
    private String targetDate;
    private List<DriverUnpaidData> data;

    /**
     * 기사별 미지급 잔액 정보를 담는 DTO입니다.
     */
    @Getter
    @Builder
    public static class DriverUnpaidData
    {
        private Long driverId;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal totalUnpaidAmount;

        @JsonFormat(
                pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
                timezone = "UTC"
        )
        private LocalDateTime lastApprovedAt;
    }
}