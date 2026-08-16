package com.example.driverledgersystem.dto;

import lombok.Builder;
import lombok.Getter;
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
public class DriverLedgerResponse
{
    private Long driverId;
    private BigDecimal totalUnpaidAmount;
    // 결제 건별 상세 내역
    private List<PaymentDetail> paymentDetails;

    /**
     * 개별 결제 건의 상세 내역을 담는 내부 DTO 클래스입니다.
     */
    @Getter
    @Setter
    @Builder
    public static class PaymentDetail
    {
        private Long paymentId;
        private BigDecimal amount;
        private LocalDateTime approvedAt;
    }
}