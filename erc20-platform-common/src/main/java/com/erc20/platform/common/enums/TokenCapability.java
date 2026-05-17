package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenCapability {
    STANDARD_RETURN("STANDARD_RETURN", "transfer/approve returns bool"),
    NO_RETURN_VALUE("NO_RETURN_VALUE", "transfer/approve does not return a value (USDT-like)"),
    APPROVE_RACE_CONDITION("APPROVE_RACE_CONDITION", "approve must be set to 0 before changing to non-zero"),
    BYTES32_METADATA("BYTES32_METADATA", "name/symbol return bytes32 instead of string"),
    PAUSABLE("PAUSABLE", "contract has pause functionality"),
    BLACKLISTABLE("BLACKLISTABLE", "contract has address blacklist functionality"),
    UPGRADEABLE("UPGRADEABLE", "contract uses proxy/upgradeable pattern"),
    MINTABLE("MINTABLE", "token supply can be increased"),
    BURNABLE("BURNABLE", "token supply can be decreased"),
    FEE_ON_TRANSFER("FEE_ON_TRANSFER", "transfers deduct a fee"),
    REBASING("REBASING", "balances change automatically without transfers"),
    MAX_TRANSFER_LIMIT("MAX_TRANSFER_LIMIT", "contract enforces maximum transfer amount"),
    COOLDOWN_REQUIRED("COOLDOWN_REQUIRED", "contract enforces minimum time between transfers");

    private final String code;
    private final String description;
}
