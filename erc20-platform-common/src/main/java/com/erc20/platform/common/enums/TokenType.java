package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenType {
    STANDARD("STANDARD", "Standard ERC-20 token"),
    FEE_ON_TRANSFER("FEE_ON_TRANSFER", "Fee-on-transfer token"),
    REBASING("REBASING", "Rebasing token"),
    UNSUPPORTED("UNSUPPORTED", "Unsupported token type");

    private final String code;
    private final String description;
}
