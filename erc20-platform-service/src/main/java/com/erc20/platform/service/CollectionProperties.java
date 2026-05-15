package com.erc20.platform.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "collection")
public class CollectionProperties {

    private boolean enabled = true;
    private BigDecimal gasBufferMultiplier = new BigDecimal("1.5");
    private String targetAddress;
    private int batchSize = 20;
    private int minIntervalHours = 4;
}
