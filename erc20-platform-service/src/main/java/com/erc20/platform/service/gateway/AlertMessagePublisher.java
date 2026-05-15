package com.erc20.platform.service.gateway;

import com.erc20.platform.service.dto.AlertMessage;

public interface AlertMessagePublisher {

    void publish(AlertMessage message);
}
