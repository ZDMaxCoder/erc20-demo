package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DepositStatus {
    PENDING("PENDING", "Waiting for confirmations"),
    CONFIRMING("CONFIRMING", "Accumulating confirmations"),
    SUCCESS("SUCCESS", "Deposit confirmed and credited"),
    BELOW_MINIMUM("BELOW_MINIMUM", "Deposit below minimum amount"),
    FAILED("FAILED", "Deposit failed"),
    REORGED("REORGED", "Deposit invalidated by chain reorganization"),
    AMOUNT_OVERFLOW("AMOUNT_OVERFLOW", "Deposit amount exceeds system capacity");

    private final String code;
    private final String description;
}
