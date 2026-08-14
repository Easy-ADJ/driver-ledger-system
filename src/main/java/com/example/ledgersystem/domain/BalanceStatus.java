package com.example.ledgersystem.domain;

public enum BalanceStatus {
    PAYABLE_POSITIVE, // 정산 대상(양수)
    ZERO,             // 지급금 없음(0원)
    ERROR_NEGATIVE    // 이상(음수)
}