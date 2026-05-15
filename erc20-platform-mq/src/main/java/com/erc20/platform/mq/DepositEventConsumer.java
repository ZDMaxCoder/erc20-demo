package com.erc20.platform.mq;

import com.erc20.platform.service.DepositService;
import com.erc20.platform.service.dto.TransferEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_BLOCK_TRANSFER_EVENT,
        selectorExpression = MqConstants.TAG_DEPOSIT,
        consumerGroup = MqConstants.GROUP_DEPOSIT_EVENT)
public class DepositEventConsumer extends BaseConsumer<TransferEventDTO>
        implements RocketMQListener<MessageExt> {

    private final DepositService depositService;

    public DepositEventConsumer(RedissonClient redissonClient, DepositService depositService) {
        super(redissonClient, MqConstants.GROUP_DEPOSIT_EVENT, TransferEventDTO.class);
        this.depositService = depositService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        String json = new String(messageExt.getBody());
        String key = messageExt.getKeys();
        handleMessage(json, key);
    }

    @Override
    protected String getMessageKey(TransferEventDTO message) {
        return message.getTxHash() + ":" + message.getLogIndex();
    }

    @Override
    protected void doConsume(TransferEventDTO message) {
        depositService.onTransferEvent(message);
    }
}
