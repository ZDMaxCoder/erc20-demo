package com.erc20.platform.service.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.risk.RiskProperties;
import com.erc20.platform.service.risk.RiskResult;
import com.erc20.platform.service.risk.RiskRule;
import org.springframework.stereotype.Component;

@Component
public class NewAddressRule implements RiskRule {

    private final RiskProperties riskProperties;
    private final WithdrawRecordMapper withdrawRecordMapper;

    public NewAddressRule(RiskProperties riskProperties, WithdrawRecordMapper withdrawRecordMapper) {
        this.riskProperties = riskProperties;
        this.withdrawRecordMapper = withdrawRecordMapper;
    }

    @Override
    public RiskResult check(WithdrawRecord record) {
        if (!riskProperties.isNewAddressReview()) {
            return RiskResult.pass();
        }
        WithdrawRecord existing = withdrawRecordMapper.selectOne(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getUserId, record.getUserId())
                        .eq(WithdrawRecord::getToAddress, record.getToAddress())
                        .eq(WithdrawRecord::getStatus, WithdrawStatus.SUCCESS.getCode())
                        .last("LIMIT 1"));
        if (existing == null) {
            return RiskResult.manualReview("First withdrawal to this address");
        }
        return RiskResult.pass();
    }

    @Override
    public int order() {
        return 4;
    }
}
