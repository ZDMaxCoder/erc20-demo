package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TxStatus {
    PENDING("PENDING", "Transaction created, not yet submitted"),
    SUBMITTED("SUBMITTED", "Transaction submitted to chain"),
    CONFIRMED("CONFIRMED", "Transaction confirmed"),
    FAILED("FAILED", "Transaction failed"),
    REPLACED("REPLACED", "Transaction replaced by a new transaction");

    private final String code;
    private final String description;
}
