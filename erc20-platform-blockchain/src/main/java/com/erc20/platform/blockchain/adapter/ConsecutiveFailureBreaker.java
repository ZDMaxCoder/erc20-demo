package com.erc20.platform.blockchain.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsecutiveFailureBreaker {

    private static final String REDIS_KEY_PREFIX = "circuit_breaker:";
    private static final String REDIS_KEY_SUFFIX = ":failures";

    private final RedissonClient redissonClient;
    private final TokenConfigMapper tokenConfigMapper;
    private final TokenRiskProfileRegistry tokenRiskProfileRegistry;
    private final AlertService alertService;
    private final int failureThreshold;

    public ConsecutiveFailureBreaker(RedissonClient redissonClient,
                                     TokenConfigMapper tokenConfigMapper,
                                     TokenRiskProfileRegistry tokenRiskProfileRegistry,
                                     AlertService alertService,
                                     @Value("${circuit-breaker.failure-threshold:5}") int failureThreshold) {
        this.redissonClient = redissonClient;
        this.tokenConfigMapper = tokenConfigMapper;
        this.tokenRiskProfileRegistry = tokenRiskProfileRegistry;
        this.alertService = alertService;
        this.failureThreshold = failureThreshold;
    }

    public void recordSuccess(String contract) {
        String normalized = contract.toLowerCase();
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey(normalized));
        counter.set(0);
    }

    public void recordFailure(String contract) {
        String normalized = contract.toLowerCase();
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey(normalized));
        long current = counter.incrementAndGet();
        if (current == failureThreshold) {
            tripBreaker(normalized);
        }
    }

    public void resetBreaker(String contract) {
        String normalized = contract.toLowerCase();
        TokenConfig config = loadTokenConfig(normalized);
        if (config == null || !"OPEN".equals(config.getCircuitBreakerStatus())) {
            return;
        }
        RAtomicLong counter = redissonClient.getAtomicLong(redisKey(normalized));
        counter.delete();
        config.setCircuitBreakerStatus("CLOSED");
        tokenConfigMapper.updateById(config);
        tokenRiskProfileRegistry.invalidate(normalized);
        log.info("Circuit breaker reset for contract {}", normalized);
    }

    private void tripBreaker(String contract) {
        TokenConfig config = loadTokenConfig(contract);
        if (config == null) {
            return;
        }
        config.setCircuitBreakerStatus("OPEN");
        tokenConfigMapper.updateById(config);
        tokenRiskProfileRegistry.invalidate(contract);
        alertService.alert(
                "TOKEN_CIRCUIT_BREAKER_TRIPPED",
                AlertLevel.CRITICAL,
                "Circuit breaker tripped for contract " + contract + " after " + failureThreshold + " consecutive failures"
        );
        log.warn("Circuit breaker tripped for contract {} after {} consecutive failures", contract, failureThreshold);
    }

    private TokenConfig loadTokenConfig(String contract) {
        return tokenConfigMapper.selectOne(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getContractAddress, contract)
        );
    }

    private String redisKey(String contract) {
        return REDIS_KEY_PREFIX + contract + REDIS_KEY_SUFFIX;
    }
}
