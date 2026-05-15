package com.erc20.platform.mq;

import com.erc20.platform.service.WithdrawService;
import com.erc20.platform.service.dto.WithdrawExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_WITHDRAW_EXECUTE,
        selectorExpression = MqConstants.TAG_APPROVED,
        consumerGroup = MqConstants.GROUP_WITHDRAW_EXECUTE)
public class WithdrawExecuteConsumer extends BaseConsumer<WithdrawExecuteMessage>
        implements RocketMQListener<MessageExt> {

    private final WithdrawService withdrawService;

    public WithdrawExecuteConsumer(RedissonClient redissonClient, WithdrawService withdrawService) {
        super(redissonClient, MqConstants.GROUP_WITHDRAW_EXECUTE, WithdrawExecuteMessage.class);
        this.withdrawService = withdrawService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        String json = new String(messageExt.getBody());
        String key = messageExt.getKeys();
        handleMessage(json, key);
    }

    @Override
    protected String getMessageKey(WithdrawExecuteMessage message) {
        return String.valueOf(message.getWithdrawId());
    }

    @Override
    protected void doConsume(WithdrawExecuteMessage message) {
        withdrawService.executeWithdraw(message.getWithdrawId());
    }
}
