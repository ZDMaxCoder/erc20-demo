package com.erc20.platform.blockchain.gas;

import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GasPriceCache {

    private static final String REDIS_KEY_PREFIX = "gas:price:";
    private static final long REDIS_TTL_SECONDS = 30;

    private final GasStrategy gasStrategy;
    private final RedissonClient redissonClient;
    private final GasProperties gasProperties;
    private final AlertService alertService;

    private final ConcurrentMap<GasPriority, GasPrice> localCache = new ConcurrentHashMap<>();

    public GasPriceCache(GasStrategy gasStrategy,
                         RedissonClient redissonClient,
                         GasProperties gasProperties,
                         AlertService alertService) {
        this.gasStrategy = gasStrategy;
        this.redissonClient = redissonClient;
        this.gasProperties = gasProperties;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelay = 15000)
    public void refreshCache() {
        BigInteger maxGasPrice = BigInteger.valueOf(gasProperties.getMaxGasPrice());
        for (GasPriority priority : GasPriority.values()) {
            try {
                GasPrice price = gasStrategy.getGasPrice(priority);
                localCache.put(priority, price);
                storeInRedis(priority, price);

                BigInteger effectivePrice = price.getGasPrice() != null
                        ? price.getGasPrice() : price.getMaxFeePerGas();
                if (effectivePrice != null && effectivePrice.compareTo(maxGasPrice) > 0) {
                    alertService.alert("GAS_ABOVE_CAP", AlertLevel.WARN,
                            "Gas price " + effectivePrice + " exceeds cap " + maxGasPrice
                                    + " for priority " + priority);
                }
            } catch (Exception e) {
                log.warn("Failed to refresh gas price cache for {}", priority, e);
            }
        }
    }

    public GasPrice getCachedGasPrice(GasPriority priority) {
        GasPrice cached = localCache.get(priority);
        if (cached != null) {
            return cached;
        }
        GasPrice fromRedis = loadFromRedis(priority);
        if (fromRedis != null) {
            localCache.put(priority, fromRedis);
            return fromRedis;
        }
        return gasStrategy.getGasPrice(priority);
    }

    public GasPrice getReplacementGasPrice(GasPrice original) {
        return gasStrategy.getReplacementGasPrice(original);
    }

    private void storeInRedis(GasPriority priority, GasPrice price) {
        try {
            String key = REDIS_KEY_PREFIX + priority.name();
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(serialize(price), REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to store gas price in Redis for {}", priority, e);
        }
    }

    private GasPrice loadFromRedis(GasPriority priority) {
        try {
            String key = REDIS_KEY_PREFIX + priority.name();
            RBucket<String> bucket = redissonClient.getBucket(key);
            String value = bucket.get();
            if (value != null) {
                return deserialize(value);
            }
        } catch (Exception e) {
            log.warn("Failed to load gas price from Redis for {}", priority, e);
        }
        return null;
    }

    private String serialize(GasPrice price) {
        StringBuilder sb = new StringBuilder();
        sb.append(price.isEip1559()).append("|");
        sb.append(price.getMaxFeePerGas() != null ? price.getMaxFeePerGas().toString() : "").append("|");
        sb.append(price.getMaxPriorityFeePerGas() != null ? price.getMaxPriorityFeePerGas().toString() : "").append("|");
        sb.append(price.getGasPrice() != null ? price.getGasPrice().toString() : "");
        return sb.toString();
    }

    private GasPrice deserialize(String value) {
        String[] parts = value.split("\\|", -1);
        return GasPrice.builder()
                .eip1559(Boolean.parseBoolean(parts[0]))
                .maxFeePerGas(parts[1].isEmpty() ? null : new BigInteger(parts[1]))
                .maxPriorityFeePerGas(parts[2].isEmpty() ? null : new BigInteger(parts[2]))
                .gasPrice(parts[3].isEmpty() ? null : new BigInteger(parts[3]))
                .build();
    }
}
