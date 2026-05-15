package com.erc20.platform.service.risk;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RiskResult {

    private final RiskStatus status;
    private final String reason;

    public static RiskResult pass() {
        return new RiskResult(RiskStatus.AUTO_PASS, null);
    }

    public static RiskResult manualReview(String reason) {
        return new RiskResult(RiskStatus.NEED_MANUAL_REVIEW, reason);
    }

    public static RiskResult reject(String reason) {
        return new RiskResult(RiskStatus.REJECT, reason);
    }
}
