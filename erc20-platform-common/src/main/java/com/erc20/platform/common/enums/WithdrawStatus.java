package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WithdrawStatus {
    PENDING_REVIEW("PENDING_REVIEW", "Awaiting approval"),
    APPROVED("APPROVED", "Approved, waiting for execution"),
    SIGNING("SIGNING", "Transaction being signed"),
    BROADCASTING("BROADCASTING", "Transaction being broadcast"),
    PENDING_CONFIRM("PENDING_CONFIRM", "Waiting for on-chain confirmation"),
    SUCCESS("SUCCESS", "Withdrawal confirmed on-chain"),
    FAILED("FAILED", "Withdrawal failed"),
    REJECTED("REJECTED", "Withdrawal rejected by risk control"),
    ANOMALY("ANOMALY", "Amount mismatch detected");

    private final String code;
    private final String description;
}
