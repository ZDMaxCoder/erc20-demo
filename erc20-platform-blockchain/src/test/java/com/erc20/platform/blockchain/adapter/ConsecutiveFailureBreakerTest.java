package com.erc20.platform.blockchain.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsecutiveFailureBreakerTest {

    @Mock private RedissonClient redissonClient;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private TokenRiskProfileRegistry tokenRiskProfileRegistry;
    @Mock private AlertService alertService;
    @Mock private RAtomicLong failureCounter;

    private ConsecutiveFailureBreaker breaker;

    private static final String CONTRACT = "0xabc123def456";
    private static final int THRESHOLD = 5;

    @BeforeEach
    void setUp() {
        breaker = new ConsecutiveFailureBreaker(
                redissonClient, tokenConfigMapper, tokenRiskProfileRegistry, alertService, THRESHOLD
        );
    }

    // --- recordSuccess ---

    @Test
    void recordSuccess_existingFailures_resetsCounterToZero() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");

        breaker.recordSuccess(CONTRACT);

        verify(failureCounter).set(0);
    }

    @Test
    void recordSuccess_normalizesAddressToLowercase() {
        String upperCase = "0xABC123DEF456";
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");

        breaker.recordSuccess(upperCase);

        verify(redissonClient).getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
    }

    // --- recordFailure: below threshold ---

    @Test
    void recordFailure_belowThreshold_incrementsCounterOnly() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
        doReturn(3L).when(failureCounter).incrementAndGet();

        breaker.recordFailure(CONTRACT);

        verify(failureCounter).incrementAndGet();
        verify(tokenConfigMapper, never()).updateById(any(TokenConfig.class));
        verify(alertService, never()).alert(anyString(), any(AlertLevel.class), anyString());
    }

    // --- recordFailure: reaches threshold → trips breaker ---

    @Test
    @SuppressWarnings("unchecked")
    void recordFailure_reachesThreshold_updatesDbToOpen() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
        doReturn((long) THRESHOLD).when(failureCounter).incrementAndGet();

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("CLOSED");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.recordFailure(CONTRACT);

        ArgumentCaptor<TokenConfig> captor = ArgumentCaptor.forClass(TokenConfig.class);
        verify(tokenConfigMapper).updateById(captor.capture());
        assertEquals("OPEN", captor.getValue().getCircuitBreakerStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordFailure_reachesThreshold_invalidatesRegistry() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
        doReturn((long) THRESHOLD).when(failureCounter).incrementAndGet();

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("CLOSED");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.recordFailure(CONTRACT);

        verify(tokenRiskProfileRegistry).invalidate(CONTRACT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordFailure_reachesThreshold_raisesCriticalAlert() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
        doReturn((long) THRESHOLD).when(failureCounter).incrementAndGet();

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("CLOSED");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.recordFailure(CONTRACT);

        verify(alertService).alert(
                eq("TOKEN_CIRCUIT_BREAKER_TRIPPED"),
                eq(AlertLevel.CRITICAL),
                contains(CONTRACT)
        );
    }

    // --- recordFailure: above threshold → no re-trip ---

    @Test
    void recordFailure_aboveThreshold_noAdditionalTrip() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
        doReturn((long) (THRESHOLD + 2)).when(failureCounter).incrementAndGet();

        breaker.recordFailure(CONTRACT);

        verify(tokenConfigMapper, never()).updateById(any(TokenConfig.class));
        verify(alertService, never()).alert(anyString(), any(AlertLevel.class), anyString());
    }

    @Test
    void recordFailure_normalizesAddressToLowercase() {
        String upperCase = "0xABC123DEF456";
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
        doReturn(1L).when(failureCounter).incrementAndGet();

        breaker.recordFailure(upperCase);

        verify(redissonClient).getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");
    }

    // --- resetBreaker: OPEN → CLOSED ---

    @Test
    @SuppressWarnings("unchecked")
    void resetBreaker_openBreaker_updatesDbToClosed() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("OPEN");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.resetBreaker(CONTRACT);

        ArgumentCaptor<TokenConfig> captor = ArgumentCaptor.forClass(TokenConfig.class);
        verify(tokenConfigMapper).updateById(captor.capture());
        assertEquals("CLOSED", captor.getValue().getCircuitBreakerStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetBreaker_openBreaker_deletesRedisCounter() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("OPEN");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.resetBreaker(CONTRACT);

        verify(failureCounter).delete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetBreaker_openBreaker_invalidatesRegistry() {
        doReturn(failureCounter).when(redissonClient)
                .getAtomicLong("circuit_breaker:" + CONTRACT + ":failures");

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("OPEN");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.resetBreaker(CONTRACT);

        verify(tokenRiskProfileRegistry).invalidate(CONTRACT);
    }

    // --- resetBreaker: already CLOSED → no-op ---

    @Test
    @SuppressWarnings("unchecked")
    void resetBreaker_alreadyClosed_noDbUpdate() {
        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("CLOSED");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.resetBreaker(CONTRACT);

        verify(tokenConfigMapper, never()).updateById(any(TokenConfig.class));
        verify(tokenRiskProfileRegistry, never()).invalidate(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetBreaker_normalizesAddressToLowercase() {
        String upperCase = "0xABC123DEF456";

        TokenConfig config = new TokenConfig();
        config.setContractAddress(CONTRACT);
        config.setCircuitBreakerStatus("CLOSED");
        doReturn(config).when(tokenConfigMapper)
                .selectOne(any(LambdaQueryWrapper.class));

        breaker.resetBreaker(upperCase);

        verify(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
    }
}
