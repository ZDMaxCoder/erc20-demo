package com.erc20.platform.service.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawLimitServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RAtomicLong dailyCounter;
    @Mock private RAtomicLong hourlyCounter;

    private RiskProperties riskProperties;
    private WithdrawLimitService limitService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        riskProperties = new RiskProperties();
        riskProperties.setDailyLimit(100000L);
        limitService = new WithdrawLimitService(redissonClient, riskProperties);
    }

    // --- 4.1 checkAndAccumulate first call → true, accumulates in Redis ---

    @Test
    void checkAndAccumulate_firstCall_returnsTrue_accumulatesInRedis() {
        doReturn(dailyCounter).when(redissonClient)
                .getAtomicLong(contains("daily"));
        doReturn(hourlyCounter).when(redissonClient)
                .getAtomicLong(contains("hourly"));
        doReturn(5000L).when(dailyCounter).addAndGet(5000L);
        doReturn(-1L).when(dailyCounter).remainTimeToLive();

        boolean result = limitService.checkAndAccumulate(USER_ID, TOKEN_ID, 5000L);

        assertTrue(result);
        verify(dailyCounter).addAndGet(5000L);
    }

    // --- 4.2 accumulate past daily limit → returns false ---

    @Test
    void checkAndAccumulate_exceedsDailyLimit_returnsFalse() {
        doReturn(dailyCounter).when(redissonClient)
                .getAtomicLong(contains("daily"));
        doReturn(110000L).when(dailyCounter).addAndGet(20000L);

        boolean result = limitService.checkAndAccumulate(USER_ID, TOKEN_ID, 20000L);

        assertFalse(result);
        verify(dailyCounter).addAndGet(-20000L);
    }

    // --- 4.3 rollback reduces accumulated amount ---

    @Test
    void rollback_reducesAccumulatedAmount() {
        doReturn(dailyCounter).when(redissonClient)
                .getAtomicLong(contains("daily"));
        doReturn(hourlyCounter).when(redissonClient)
                .getAtomicLong(contains("hourly"));

        limitService.rollback(USER_ID, TOKEN_ID, 5000L);

        verify(dailyCounter).addAndGet(-5000L);
        verify(hourlyCounter).decrementAndGet();
    }

    // --- 4.4 Redis key has correct TTL (daily=48h, hourly=2h) ---

    @Test
    void checkAndAccumulate_setsCorrectTtl() {
        doReturn(dailyCounter).when(redissonClient)
                .getAtomicLong(contains("daily"));
        doReturn(hourlyCounter).when(redissonClient)
                .getAtomicLong(contains("hourly"));
        doReturn(5000L).when(dailyCounter).addAndGet(5000L);
        doReturn(-1L).when(dailyCounter).remainTimeToLive();
        doReturn(-1L).when(hourlyCounter).remainTimeToLive();

        limitService.checkAndAccumulate(USER_ID, TOKEN_ID, 5000L);

        verify(dailyCounter).expire(48, TimeUnit.HOURS);
        verify(hourlyCounter).expire(2, TimeUnit.HOURS);
    }
}
