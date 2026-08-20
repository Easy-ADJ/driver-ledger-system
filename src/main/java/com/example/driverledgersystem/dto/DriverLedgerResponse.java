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
 * GET /api/ledger?driver_id= API의 응답 데이터를 담는 DTO 클래스입니다.
 * 기사별 미지급금 합계와 결제 건별 내역을 정산 서버로 전달합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLedgerResponse
{
    private Long driverId;

    // 부동소수점 오차 방지를 위해 JSON 응답 시 문자열(String)로 변환
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalUnpaidAmount;

    // 결제 건별 상세 내역
    private List<PaymentDetail> paymentDetails;

    /**
     * 개별 결제 건의 상세 내역을 담는 내부 DTO 클래스입니다.
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

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        private LocalDateTime approvedAt;
    }
}