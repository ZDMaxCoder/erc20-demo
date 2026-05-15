package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CollectionTaskStatus {
    PENDING("PENDING", "Task created, waiting to execute"),
    GAS_SUPPLYING("GAS_SUPPLYING", "Gas supply transaction sent"),
    GAS_CONFIRMED("GAS_CONFIRMED", "Gas supply confirmed, ready to collect"),
    COLLECTING("COLLECTING", "Collection transaction sent"),
    SUCCESS("SUCCESS", "Collection completed"),
    FAILED("FAILED", "Task failed, retryable");

    private final String code;
    private final String description;
}
