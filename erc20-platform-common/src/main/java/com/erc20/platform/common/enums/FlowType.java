package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FlowType {
    DEPOSIT("DEPOSIT", "Deposit credited"),
    WITHDRAW("WITHDRAW", "Withdrawal debited"),
    WITHDRAW_FEE("WITHDRAW_FEE", "Withdrawal fee"),
    FREEZE("FREEZE", "Balance frozen"),
    UNFREEZE("UNFREEZE", "Balance unfrozen"),
    COLLECTION("COLLECTION", "Token collection"),
    COLLECTION_FEE("COLLECTION_FEE", "Collection fee"),
    ADJUSTMENT("ADJUSTMENT", "Manual adjustment");

    private final String code;
    private final String description;
}
