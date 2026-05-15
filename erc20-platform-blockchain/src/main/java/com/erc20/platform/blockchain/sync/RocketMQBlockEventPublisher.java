package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.erc20.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RocketMQBlockEventPublisher implements BlockEventPublisher {

    private static final String TOPIC = "BLOCK_TRANSFER_EVENT:DEPOSIT";

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMQBlockEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void publish(List<TransferEvent> events) {
        for (TransferEvent event : events) {
            TransferEventMessage message = TransferEventMessage.builder()
                    .contractAddress(event.getContractAddress())
                    .from(event.getFrom())
                    .to(event.getTo())
                    .value(event.getValue())
                    .txHash(event.getTxHash())
                    .blockNumber(event.getBlockNumber())
                    .logIndex(event.getLogIndex())
                    .build();

            Message<TransferEventMessage> msg = MessageBuilder
                    .withPayload(message)
                    .setHeader("KEYS", event.getTxHash())
                    .build();

            rocketMQTemplate.syncSend(TOPIC, msg);
            log.debug("Published transfer event: txHash={}, block={}", event.getTxHash(), event.getBlockNumber());
        }
    }
}
