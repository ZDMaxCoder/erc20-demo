package com.erc20.platform.mq;

import com.alibaba.fastjson.JSON;
import com.erc20.platform.service.dto.WithdrawExecuteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseConsumerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    private TestConsumer consumer;
    private boolean doConsumeInvoked;
    private WithdrawExecuteMessage lastConsumedMessage;

    @BeforeEach
    void setUp() {
        doConsumeInvoked = false;
        lastConsumedMessage = null;
        consumer = new TestConsumer(redissonClient);
    }

    // --- 4.1: first message → doConsume called, key added to Redis ---

    @Test
    void onMessage_firstMessage_consumedAndRedisKeySet() {
        WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                .withdrawId(1L)
                .build();
        String json = JSON.toJSONString(payload);

        doReturn(bucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(bucket).isExists();

        consumer.handleMessage(json, "key-1");

        assertTrue(doConsumeInvoked);
        assertEquals(1L, lastConsumedMessage.getWithdrawId().longValue());
        verify(bucket).set(eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    // --- 4.2: same message key second time → doConsume NOT called ---

    @Test
    void onMessage_duplicateKey_skipped() {
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        doReturn(true).when(bucket).isExists();

        consumer.handleMessage("{\"withdrawId\":2}", "key-2");

        assertFalse(doConsumeInvoked);
    }

    // --- 4.3: doConsume throws retryable exception → propagated for MQ retry ---

    @Test
    void onMessage_retryableException_propagated() {
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(bucket).isExists();

        consumer.throwOnConsume = new RuntimeException("retryable error");

        assertThrows(RuntimeException.class, () ->
                consumer.handleMessage("{\"withdrawId\":3}", "key-3"));

        verify(bucket, never()).set(anyString(), anyLong(), any(TimeUnit.class));
    }

    // --- 4.4: doConsume throws non-retryable BizException → consumed (no retry), logged ERROR ---

    @Test
    void onMessage_nonRetryableException_consumed() {
        doReturn(bucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(bucket).isExists();

        consumer.throwOnConsume = new com.erc20.platform.common.exception.BizException(500, "non-retryable");

        consumer.handleMessage("{\"withdrawId\":4}", "key-4");

        verify(bucket).set(eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    class TestConsumer extends BaseConsumer<WithdrawExecuteMessage> {

        RuntimeException throwOnConsume;

        TestConsumer(RedissonClient redissonClient) {
            super(redissonClient, "test-group", WithdrawExecuteMessage.class);
        }

        @Override
        protected String getMessageKey(WithdrawExecuteMessage message) {
            return String.valueOf(message.getWithdrawId());
        }

        @Override
        protected void doConsume(WithdrawExecuteMessage message) {
            if (throwOnConsume != null) {
                throw throwOnConsume;
            }
            doConsumeInvoked = true;
            lastConsumedMessage = message;
        }
    }
}
