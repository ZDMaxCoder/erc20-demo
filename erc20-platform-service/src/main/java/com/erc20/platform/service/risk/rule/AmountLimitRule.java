package com.erc20.platform.service.risk.rule;

import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.risk.RiskProperties;
import com.erc20.platform.service.risk.RiskResult;
import com.erc20.platform.service.risk.RiskRule;
import com.erc20.platform.service.risk.WithdrawLimitService;
import org.springframework.stereotype.Component;

@Component
public class AmountLimitRule implements RiskRule {

    private final RiskProperties riskProperties;
    private final WithdrawLimitService limitService;

    public AmountLimitRule(RiskProperties riskProperties, WithdrawLimitService limitService) {
        this.riskProperties = riskProperties;
        this.limitService = limitService;
    }

    @Override
    public RiskResult check(WithdrawRecord record) {
        boolean withinLimit = limitService.checkAndAccumulate(
                record.getUserId(), record.getTokenId(), record.getAmount());
        if (!withinLimit) {
            return RiskResult.reject("Daily withdrawal limit exceeded");
        }
        if (record.getAmount() > riskProperties.getAutoPassMaxAmount()) {
            return RiskResult.manualReview("Amount exceeds auto-pass threshold");
        }
        return RiskResult.pass();
    }

    @Override
    public int order() {
        return 2;
    }
}
