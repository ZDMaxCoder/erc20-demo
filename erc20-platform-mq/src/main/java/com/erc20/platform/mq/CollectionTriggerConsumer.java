package com.erc20.platform.mq;

import com.erc20.platform.service.CollectionTriggerService;
import com.erc20.platform.service.dto.DepositConfirmedMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_DEPOSIT_CONFIRMED,
        consumerGroup = MqConstants.GROUP_DEPOSIT_CONFIRMED)
public class CollectionTriggerConsumer extends BaseConsumer<DepositConfirmedMessage>
        implements RocketMQListener<MessageExt> {

    private final CollectionTriggerService triggerService;

    public CollectionTriggerConsumer(RedissonClient redissonClient,
                                     CollectionTriggerService triggerService) {
        super(redissonClient, MqConstants.GROUP_DEPOSIT_CONFIRMED, DepositConfirmedMessage.class);
        this.triggerService = triggerService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        String json = new String(messageExt.getBody());
        String key = messageExt.getKeys();
        handleMessage(json, key);
    }

    @Override
    protected String getMessageKey(DepositConfirmedMessage message) {
        return String.valueOf(message.getDepositId());
    }

    @Override
    protected void doConsume(DepositConfirmedMessage message) {
        triggerService.onDepositConfirmed(message);
    }
}
