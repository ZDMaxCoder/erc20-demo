package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WalletType {
    HOT("HOT", "Hot wallet for withdrawals"),
    COLD("COLD", "Cold wallet for storage"),
    GAS("GAS", "Gas supply wallet");

    private final String code;
    private final String description;
}
