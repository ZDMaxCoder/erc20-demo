package com.erc20.platform.service.risk.rule;

import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.risk.AddressBlacklistService;
import com.erc20.platform.service.risk.RiskResult;
import com.erc20.platform.service.risk.RiskRule;
import org.springframework.stereotype.Component;

@Component
public class AddressBlacklistRule implements RiskRule {

    private final AddressBlacklistService blacklistService;

    public AddressBlacklistRule(AddressBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @Override
    public RiskResult check(WithdrawRecord record) {
        if (blacklistService.isBlacklisted(record.getToAddress())) {
            return RiskResult.reject("Address is blacklisted: " + record.getToAddress());
        }
        return RiskResult.pass();
    }

    @Override
    public int order() {
        return 1;
    }
}
