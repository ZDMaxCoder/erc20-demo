package com.erc20.platform.mq;

import com.erc20.platform.service.dto.WithdrawExecuteMessage;
import com.erc20.platform.service.gateway.WithdrawMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WithdrawMessagePublisherImpl implements WithdrawMessagePublisher {

    private final MqProducer mqProducer;

    public WithdrawMessagePublisherImpl(MqProducer mqProducer) {
        this.mqProducer = mqProducer;
    }

    @Override
    public void sendExecuteMessage(long withdrawId) {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(withdrawId)
                .build();

        mqProducer.send(MqConstants.TOPIC_WITHDRAW_EXECUTE, MqConstants.TAG_APPROVED,
                String.valueOf(withdrawId), payload);
    }
}
