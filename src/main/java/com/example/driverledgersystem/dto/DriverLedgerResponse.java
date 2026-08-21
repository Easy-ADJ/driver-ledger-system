package com.example.driverledgersystem.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기사별 원장 조회 API의 응답 데이터를 담는 DTO입니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLedgerResponse
{
    private Long driverId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalUnpaidAmount;

    private List<PaymentDetail> paymentDetails;

    /**
     * 기사별 결제 근거 정보를 담는 DTO입니다.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDetail
    {
        private Long paymentId;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal amount;

        @JsonFormat(
                pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
                timezone = "UTC"
        )
        private LocalDateTime approvedAt;
    }
}