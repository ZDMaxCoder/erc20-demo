package com.erc20.platform.service;

import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.AlertRecordMapper;
import com.erc20.platform.domain.entity.AlertRecord;
import com.erc20.platform.service.dto.AlertMessage;
import com.erc20.platform.service.gateway.AlertMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRecordMapper alertRecordMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private AlertMessagePublisher alertMessagePublisher;
    @Mock
    private RBucket<String> dedupBucket;

    private AlertProperties alertProperties;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertProperties = new AlertProperties();
        alertProperties.setDedupIntervalMinutes(10);
        alertService = new AlertService(alertRecordMapper, redissonClient,
                alertMessagePublisher, alertProperties);
    }

    // 4.1: alert(type, CRITICAL, content) → record saved, MQ published
    @Test
    void alert_critical_savedAndPublished() {
        doReturn(dedupBucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(dedupBucket).isExists();

        alertService.alert("REORG", AlertLevel.CRITICAL, "Chain reorg detected at block 100");

        ArgumentCaptor<AlertRecord> recordCaptor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(recordCaptor.capture());
        AlertRecord saved = recordCaptor.getValue();
        assertEquals("REORG", saved.getAlertType());
        assertEquals(AlertLevel.CRITICAL.getCode(), saved.getAlertLevel());
        assertEquals("Chain reorg detected at block 100", saved.getContent());

        ArgumentCaptor<AlertMessage> msgCaptor = ArgumentCaptor.forClass(AlertMessage.class);
        verify(alertMessagePublisher).publish(msgCaptor.capture());
        AlertMessage msg = msgCaptor.getValue();
        assertEquals("REORG", msg.getAlertType());
        assertEquals(AlertLevel.CRITICAL.getCode(), msg.getAlertLevel());
    }

    // 4.2: same type+level within 10 minutes → deduplicated
    @Test
    void alert_duplicateWithinDedupWindow_skipped() {
        doReturn(dedupBucket).when(redissonClient).getBucket(anyString());
        doReturn(true).when(dedupBucket).isExists();

        alertService.alert("REORG", AlertLevel.CRITICAL, "Chain reorg again");

        verify(alertRecordMapper, never()).insert(any());
        verify(alertMessagePublisher, never()).publish(any());
    }

    // 4.3: same type+level after 10 minutes → new alert created
    @Test
    void alert_afterDedupExpiry_newAlertCreated() {
        doReturn(dedupBucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(dedupBucket).isExists();

        alertService.alert("REORG", AlertLevel.CRITICAL, "Chain reorg after 10 min");

        verify(alertRecordMapper).insert(any(AlertRecord.class));
        verify(alertMessagePublisher).publish(any(AlertMessage.class));
    }

    // 4.4: INFO level → saved but NOT published to MQ
    @Test
    void alert_infoLevel_savedButNotPublished() {
        doReturn(dedupBucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(dedupBucket).isExists();

        alertService.alert("LARGE_WITHDRAW", AlertLevel.INFO, "Large withdrawal pending review");

        verify(alertRecordMapper).insert(any(AlertRecord.class));
        verify(alertMessagePublisher, never()).publish(any());
    }

    // 4.1 supplement: WARN level → saved AND published
    @Test
    void alert_warnLevel_savedAndPublished() {
        doReturn(dedupBucket).when(redissonClient).getBucket(anyString());
        doReturn(false).when(dedupBucket).isExists();

        alertService.alert("NONCE_GAP", AlertLevel.WARN, "Nonce gap detected");

        verify(alertRecordMapper).insert(any(AlertRecord.class));
        verify(alertMessagePublisher).publish(any(AlertMessage.class));
    }
}
