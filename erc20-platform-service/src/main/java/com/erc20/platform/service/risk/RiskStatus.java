package com.erc20.platform.service.risk;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RiskStatus {
    AUTO_PASS("AUTO_PASS", "Risk check passed automatically"),
    NEED_MANUAL_REVIEW("NEED_MANUAL_REVIEW", "Requires manual review"),
    REJECT("REJECT", "Rejected by risk control");

    private final String code;
    private final String description;
}
