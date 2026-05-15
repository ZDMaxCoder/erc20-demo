package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AddressStatus {
    AVAILABLE("AVAILABLE", "Available for allocation"),
    BOUND("BOUND", "Bound to a user"),
    DISABLED("DISABLED", "Disabled");

    private final String code;
    private final String description;
}
