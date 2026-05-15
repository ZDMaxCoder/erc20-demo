package com.erc20.platform.mq;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MqProducer {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_INTERVAL_MS = 1000;

    private final RocketMQTemplate rocketMQTemplate;

    public MqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    public void send(String topic, String tag, String key, Object payload) {
        String destination = tag != null && !tag.isEmpty() ? topic + ":" + tag : topic;
        String jsonBody = JSON.toJSONString(payload);

        Message<String> message = MessageBuilder
                .withPayload(jsonBody)
                .setHeader("KEYS", key)
                .build();

        RuntimeException lastException = null;
        for (int i = 1; i <= MAX_RETRIES; i++) {
            try {
                rocketMQTemplate.syncSend(destination, message);
                log.info("MQ send success: destination={}, key={}", destination, key);
                return;
            } catch (RuntimeException e) {
                lastException = e;
                log.warn("MQ send failed (attempt {}/{}): destination={}, key={}",
                        i, MAX_RETRIES, destination, key, e);
                if (i < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        log.error("MQ send failed after {} retries: destination={}, key={}",
                MAX_RETRIES, destination, key, lastException);
        throw lastException;
    }

    public void sendDelay(String topic, String tag, String key, Object payload, int delayLevel) {
        String destination = tag != null && !tag.isEmpty() ? topic + ":" + tag : topic;
        String jsonBody = JSON.toJSONString(payload);

        Message<String> message = MessageBuilder
                .withPayload(jsonBody)
                .setHeader("KEYS", key)
                .build();

        rocketMQTemplate.syncSend(destination, message, 3000, delayLevel);
        log.info("MQ sendDelay success: destination={}, key={}, delayLevel={}", destination, key, delayLevel);
    }

    public void sendOrderly(String topic, String tag, String key, Object payload, String hashKey) {
        String destination = tag != null && !tag.isEmpty() ? topic + ":" + tag : topic;
        String jsonBody = JSON.toJSONString(payload);

        Message<String> message = MessageBuilder
                .withPayload(jsonBody)
                .setHeader("KEYS", key)
                .build();

        rocketMQTemplate.syncSendOrderly(destination, message, hashKey);
        log.info("MQ sendOrderly success: destination={}, key={}, hashKey={}", destination, key, hashKey);
    }
}
