package com.erc20.platform.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    private int dedupIntervalMinutes = 10;
}
