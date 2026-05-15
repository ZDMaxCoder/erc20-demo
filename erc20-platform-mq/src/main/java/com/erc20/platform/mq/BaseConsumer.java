package com.erc20.platform.mq;

import com.alibaba.fastjson.JSON;
import com.erc20.platform.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class BaseConsumer<T> {

    private final RedissonClient redissonClient;
    private final String consumerGroup;
    private final Class<T> messageType;

    protected BaseConsumer(RedissonClient redissonClient, String consumerGroup, Class<T> messageType) {
        this.redissonClient = redissonClient;
        this.consumerGroup = consumerGroup;
        this.messageType = messageType;
    }

    public void handleMessage(String json, String messageKey) {
        String redisKey = MqConstants.REDIS_CONSUMED_PREFIX + consumerGroup + ":" + messageKey;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);

        if (bucket.isExists()) {
            log.info("Duplicate message skipped: group={}, key={}", consumerGroup, messageKey);
            return;
        }

        T message = JSON.parseObject(json, messageType);

        try {
            doConsume(message);
        } catch (BizException e) {
            log.error("Non-retryable business error consuming message: group={}, key={}",
                    consumerGroup, messageKey, e);
            bucket.set("1", MqConstants.REDIS_CONSUMED_TTL_HOURS, TimeUnit.HOURS);
            return;
        } catch (RuntimeException e) {
            log.error("Retryable error consuming message: group={}, key={}",
                    consumerGroup, messageKey, e);
            throw e;
        }

        bucket.set("1", MqConstants.REDIS_CONSUMED_TTL_HOURS, TimeUnit.HOURS);
        log.info("Message consumed: group={}, key={}", consumerGroup, messageKey);
    }

    protected abstract String getMessageKey(T message);

    protected abstract void doConsume(T message);
}
