package com.erc20.platform.service.risk;

import com.erc20.platform.domain.entity.WithdrawRecord;

public interface RiskRule {

    RiskResult check(WithdrawRecord record);

    int order();
}
