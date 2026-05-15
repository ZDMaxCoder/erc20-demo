package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.erc20.TransferEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RocketMQBlockEventPublisherTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private RocketMQBlockEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RocketMQBlockEventPublisher(rocketMQTemplate);
    }

    // --- Task 8.1: TransferEventMessage published to correct topic:tag with correct fields ---

    @Test
    void publish_sendsMessageToCorrectTopicWithTxHashAsKey() {
        TransferEvent event = TransferEvent.builder()
                .contractAddress("0xtoken")
                .from("0xfrom")
                .to("0xto")
                .value(BigInteger.valueOf(1000))
                .txHash("0xtxhash123")
                .blockNumber(100L)
                .logIndex(0)
                .build();

        publisher.publish(Collections.singletonList(event));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<TransferEventMessage>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq("BLOCK_TRANSFER_EVENT:DEPOSIT"), messageCaptor.capture());

        Message<TransferEventMessage> sent = messageCaptor.getValue();
        TransferEventMessage payload = sent.getPayload();
        assertEquals("0xtoken", payload.getContractAddress());
        assertEquals("0xfrom", payload.getFrom());
        assertEquals("0xto", payload.getTo());
        assertEquals(BigInteger.valueOf(1000), payload.getValue());
        assertEquals("0xtxhash123", payload.getTxHash());
        assertEquals(100L, payload.getBlockNumber());
        assertEquals(0, payload.getLogIndex());

        // Verify tx_hash used as message key
        assertEquals("0xtxhash123", sent.getHeaders().get("KEYS"));
    }

    @Test
    void publish_multipleEvents_sendsEachMessage() {
        TransferEvent event1 = TransferEvent.builder()
                .contractAddress("0xtoken").txHash("0xtx1").blockNumber(100L).logIndex(0)
                .from("0xa").to("0xb").value(BigInteger.ONE).build();
        TransferEvent event2 = TransferEvent.builder()
                .contractAddress("0xtoken").txHash("0xtx2").blockNumber(100L).logIndex(1)
                .from("0xc").to("0xd").value(BigInteger.TEN).build();

        publisher.publish(Arrays.asList(event1, event2));

        verify(rocketMQTemplate, times(2)).syncSend(eq("BLOCK_TRANSFER_EVENT:DEPOSIT"), any(Message.class));
    }

    @Test
    void publish_emptyList_noMessageSent() {
        publisher.publish(Collections.emptyList());

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
    }
}
