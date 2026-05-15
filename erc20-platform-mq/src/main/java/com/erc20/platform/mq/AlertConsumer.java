package com.erc20.platform.mq;

import com.erc20.platform.dal.mapper.AlertRecordMapper;
import com.erc20.platform.domain.entity.AlertRecord;
import com.erc20.platform.service.dto.AlertMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_PLATFORM_ALERT,
        consumerGroup = MqConstants.GROUP_PLATFORM_ALERT)
public class AlertConsumer extends BaseConsumer<AlertMessage>
        implements RocketMQListener<MessageExt> {

    private final AlertRecordMapper alertRecordMapper;

    public AlertConsumer(RedissonClient redissonClient, AlertRecordMapper alertRecordMapper) {
        super(redissonClient, MqConstants.GROUP_PLATFORM_ALERT, AlertMessage.class);
        this.alertRecordMapper = alertRecordMapper;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        String json = new String(messageExt.getBody());
        String key = messageExt.getKeys();
        handleMessage(json, key);
    }

    @Override
    protected String getMessageKey(AlertMessage message) {
        return message.getAlertType() + ":" + message.getTimestamp();
    }

    @Override
    protected void doConsume(AlertMessage message) {
        AlertRecord record = AlertRecord.builder()
                .alertLevel(message.getAlertLevel())
                .alertType(message.getAlertType())
                .content(message.getContent())
                .resolved(0)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        alertRecordMapper.insert(record);
        log.info("Alert persisted: type={}, level={}", message.getAlertType(), message.getAlertLevel());
    }
}
