package com.erc20.platform.mq;

import com.alibaba.fastjson.JSON;
import com.erc20.platform.service.dto.WithdrawExecuteMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqProducerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private MqProducer mqProducer;

    @BeforeEach
    void setUp() {
        mqProducer = new MqProducer(rocketMQTemplate);
    }

    // --- 2.1: send with valid message → correct destination, JSON body, message key ---

    @Test
    void send_validMessage_sendsToCorrectDestinationWithCorrectBodyAndKey() {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(100L)
                .build();

        mqProducer.send(MqConstants.TOPIC_WITHDRAW_EXECUTE, MqConstants.TAG_APPROVED,
                "withdraw-100", payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(
                eq(MqConstants.TOPIC_WITHDRAW_EXECUTE + ":" + MqConstants.TAG_APPROVED),
                messageCaptor.capture());

        Message<String> sent = messageCaptor.getValue();
        String jsonBody = sent.getPayload();
        WithdrawExecuteMessage deserialized = JSON.parseObject(jsonBody, WithdrawExecuteMessage.class);
        assertEquals(100L, deserialized.getWithdrawId().longValue());
        assertEquals("withdraw-100", sent.getHeaders().get("KEYS"));
    }

    @Test
    void send_noTag_sendsToTopicOnly() {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(200L)
                .build();

        mqProducer.send(MqConstants.TOPIC_DEPOSIT_CONFIRMED, null, "deposit-200", payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(
                eq(MqConstants.TOPIC_DEPOSIT_CONFIRMED),
                messageCaptor.capture());

        Message<String> sent = messageCaptor.getValue();
        assertNotNull(sent.getPayload());
    }

    // --- 2.2: send fails 2 times then succeeds on 3rd → message eventually sent ---

    @Test
    void send_failsTwiceThenSucceeds_retriesAndEventuallySends() {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(300L)
                .build();

        doThrow(new RuntimeException("send fail 1"))
                .doThrow(new RuntimeException("send fail 2"))
                .doReturn(null)
                .when(rocketMQTemplate).syncSend(anyString(), any(Message.class));

        mqProducer.send(MqConstants.TOPIC_WITHDRAW_EXECUTE, MqConstants.TAG_APPROVED,
                "withdraw-300", payload);

        verify(rocketMQTemplate, times(3)).syncSend(anyString(), any(Message.class));
    }

    // --- 2.3: send fails all 3 retries → exception thrown ---

    @Test
    void send_failsAllRetries_throwsException() {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(400L)
                .build();

        doThrow(new RuntimeException("send fail"))
                .when(rocketMQTemplate).syncSend(anyString(), any(Message.class));

        assertThrows(RuntimeException.class, () ->
                mqProducer.send(MqConstants.TOPIC_WITHDRAW_EXECUTE, MqConstants.TAG_APPROVED,
                        "withdraw-400", payload));

        verify(rocketMQTemplate, times(3)).syncSend(anyString(), any(Message.class));
    }

    // --- 2.4: sendOrderly uses syncSendOrderly with correct hashKey ---

    @Test
    void sendOrderly_sendsWithCorrectHashKey() {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(500L)
                .build();

        mqProducer.sendOrderly(MqConstants.TOPIC_WITHDRAW_EXECUTE, MqConstants.TAG_APPROVED,
                "withdraw-500", payload, "hash-key-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSendOrderly(
                eq(MqConstants.TOPIC_WITHDRAW_EXECUTE + ":" + MqConstants.TAG_APPROVED),
                messageCaptor.capture(),
                eq("hash-key-123"));

        Message<String> sent = messageCaptor.getValue();
        assertEquals("withdraw-500", sent.getHeaders().get("KEYS"));
    }
}
