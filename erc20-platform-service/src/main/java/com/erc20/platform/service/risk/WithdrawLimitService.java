package com.erc20.platform.service.risk;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WithdrawLimitService {

    private static final String DAILY_KEY = "risk:withdraw:daily:%s:%d";
    private static final String HOURLY_COUNT_KEY = "risk:withdraw:hourly:count:%s:%d";

    private final RedissonClient redissonClient;
    private final RiskProperties riskProperties;

    public WithdrawLimitService(RedissonClient redissonClient, RiskProperties riskProperties) {
        this.redissonClient = redissonClient;
        this.riskProperties = riskProperties;
    }

    public boolean checkAndAccumulate(String userId, long tokenId, long amount) {
        RAtomicLong dailyCounter = redissonClient.getAtomicLong(
                String.format(DAILY_KEY, userId, tokenId));

        long newTotal = dailyCounter.addAndGet(amount);
        if (newTotal > riskProperties.getDailyLimit()) {
            dailyCounter.addAndGet(-amount);
            return false;
        }

        if (dailyCounter.remainTimeToLive() == -1) {
            dailyCounter.expire(48, TimeUnit.HOURS);
        }

        RAtomicLong hourlyCounter = redissonClient.getAtomicLong(
                String.format(HOURLY_COUNT_KEY, userId, tokenId));
        hourlyCounter.incrementAndGet();
        if (hourlyCounter.remainTimeToLive() == -1) {
            hourlyCounter.expire(2, TimeUnit.HOURS);
        }

        return true;
    }

    public void rollback(String userId, long tokenId, long amount) {
        RAtomicLong dailyCounter = redissonClient.getAtomicLong(
                String.format(DAILY_KEY, userId, tokenId));
        dailyCounter.addAndGet(-amount);

        RAtomicLong hourlyCounter = redissonClient.getAtomicLong(
                String.format(HOURLY_COUNT_KEY, userId, tokenId));
        hourlyCounter.decrementAndGet();
    }
}
