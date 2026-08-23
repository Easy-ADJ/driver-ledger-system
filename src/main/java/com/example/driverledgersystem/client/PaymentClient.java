package com.example.driverledgersystem.client;

import com.example.driverledgersystem.dto.PaymentLedgerResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

/**
 * 결제 서버의 결제 내역 API를 호출합니다.
 */
@Component
public class PaymentClient
{
    private final RestClient restClient;

    public PaymentClient(
            @Value("${payment.server.base-url}")
            String paymentServerBaseUrl
    )
    {
        this.restClient =
                RestClient.builder()
                        .baseUrl(paymentServerBaseUrl)
                        .build();
    }

    /**
     * 특정 기사의 결제 내역을 결제 서버에서 조회합니다.
     *
     * @param driverId 기사 ID
     * @return 결제 내역 목록
     */
    public List<PaymentLedgerResponse> getPayments(Long driverId)
    {
        PaymentLedgerResponse[] response =
                restClient.get()
                        .uri(uriBuilder ->
                                uriBuilder
                                        .path("/api/ledger")
                                        .queryParam(
                                                "driver_id",
                                                driverId
                                        )
                                        .build()
                        )
                        .retrieve()
                        .body(PaymentLedgerResponse[].class);

        if (response == null)
        {
            return List.of();
        }

        return Arrays.asList(response);
    }
}