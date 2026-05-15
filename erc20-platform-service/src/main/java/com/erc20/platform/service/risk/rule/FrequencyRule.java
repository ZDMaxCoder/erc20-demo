package com.erc20.platform.service.risk.rule;

import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.risk.RiskProperties;
import com.erc20.platform.service.risk.RiskResult;
import com.erc20.platform.service.risk.RiskRule;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class FrequencyRule implements RiskRule {

    private static final String HOURLY_COUNT_KEY = "risk:withdraw:hourly:count:%s:%d";

    private final RiskProperties riskProperties;
    private final RedissonClient redissonClient;

    public FrequencyRule(RiskProperties riskProperties, RedissonClient redissonClient) {
        this.riskProperties = riskProperties;
        this.redissonClient = redissonClient;
    }

    @Override
    public RiskResult check(WithdrawRecord record) {
        String key = String.format(HOURLY_COUNT_KEY, record.getUserId(), record.getTokenId());
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long count = counter.get();
        if (count >= riskProperties.getHourlyMaxCount()) {
            return RiskResult.manualReview("Hourly withdrawal frequency exceeded");
        }
        return RiskResult.pass();
    }

    @Override
    public int order() {
        return 3;
    }
}
