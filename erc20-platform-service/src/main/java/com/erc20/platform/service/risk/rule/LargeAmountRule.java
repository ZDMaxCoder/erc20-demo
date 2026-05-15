package com.erc20.platform.service.risk.rule;

import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.risk.RiskProperties;
import com.erc20.platform.service.risk.RiskResult;
import com.erc20.platform.service.risk.RiskRule;
import org.springframework.stereotype.Component;

@Component
public class LargeAmountRule implements RiskRule {

    private final RiskProperties riskProperties;

    public LargeAmountRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    @Override
    public RiskResult check(WithdrawRecord record) {
        if (record.getAmount() >= riskProperties.getLargeAmountThreshold()) {
            return RiskResult.manualReview("Large amount withdrawal");
        }
        return RiskResult.pass();
    }

    @Override
    public int order() {
        return 5;
    }
}
