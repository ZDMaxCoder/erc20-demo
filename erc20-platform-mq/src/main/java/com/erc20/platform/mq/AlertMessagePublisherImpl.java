package com.erc20.platform.mq;

import com.erc20.platform.service.dto.AlertMessage;
import com.erc20.platform.service.gateway.AlertMessagePublisher;
import org.springframework.stereotype.Component;

@Component
public class AlertMessagePublisherImpl implements AlertMessagePublisher {

    private final MqProducer mqProducer;

    public AlertMessagePublisherImpl(MqProducer mqProducer) {
        this.mqProducer = mqProducer;
    }

    @Override
    public void publish(AlertMessage message) {
        String key = message.getAlertType() + "_" + message.getTimestamp();
        mqProducer.send(MqConstants.TOPIC_PLATFORM_ALERT, null, key, message);
    }
}
