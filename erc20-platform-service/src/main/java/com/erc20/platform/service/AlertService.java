package com.erc20.platform.service;

import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.AlertRecordMapper;
import com.erc20.platform.domain.entity.AlertRecord;
import com.erc20.platform.service.dto.AlertMessage;
import com.erc20.platform.service.gateway.AlertMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AlertService {

    private static final String DEDUP_KEY_PREFIX = "alert:dedup:";

    private final AlertRecordMapper alertRecordMapper;
    private final RedissonClient redissonClient;
    private final AlertMessagePublisher alertMessagePublisher;
    private final AlertProperties alertProperties;

    public AlertService(AlertRecordMapper alertRecordMapper,
                        RedissonClient redissonClient,
                        AlertMessagePublisher alertMessagePublisher,
                        AlertProperties alertProperties) {
        this.alertRecordMapper = alertRecordMapper;
        this.redissonClient = redissonClient;
        this.alertMessagePublisher = alertMessagePublisher;
        this.alertProperties = alertProperties;
    }

    public void alert(String alertType, AlertLevel level, String content, String bizId) {
        String suffix = (bizId != null && !bizId.isEmpty()) ? ":" + bizId : "";
        String dedupKey = DEDUP_KEY_PREFIX + alertType + ":" + level.getCode() + suffix;
        doAlert(alertType, level, content, dedupKey);
    }

    public void alert(String alertType, AlertLevel level, String content) {
        String dedupKey = DEDUP_KEY_PREFIX + alertType + ":" + level.getCode();
        doAlert(alertType, level, content, dedupKey);
    }

    private void doAlert(String alertType, AlertLevel level, String content, String dedupKey) {
        RBucket<String> bucket = redissonClient.getBucket(dedupKey);

        if (bucket.isExists()) {
            log.debug("Alert deduplicated: type={}, level={}", alertType, level);
            return;
        }

        bucket.set("1", alertProperties.getDedupIntervalMinutes(), TimeUnit.MINUTES);

        AlertRecord record = AlertRecord.builder()
                .alertType(alertType)
                .alertLevel(level.getCode())
                .title(alertType)
                .content(content)
                .resolved(0)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        alertRecordMapper.insert(record);

        if (level == AlertLevel.WARN || level == AlertLevel.CRITICAL) {
            AlertMessage message = AlertMessage.builder()
                    .alertType(alertType)
                    .alertLevel(level.getCode())
                    .content(content)
                    .source("platform")
                    .timestamp(System.currentTimeMillis())
                    .build();
            alertMessagePublisher.publish(message);
        }

        log.info("Alert raised: type={}, level={}, content={}", alertType, level, content);
    }
}
