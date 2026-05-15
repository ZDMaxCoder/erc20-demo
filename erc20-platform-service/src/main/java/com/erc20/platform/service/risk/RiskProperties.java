package com.erc20.platform.service.risk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "risk.withdraw")
public class RiskProperties {

    private long autoPassMaxAmount = 10000L;
    private long dailyLimit = 100000L;
    private int hourlyMaxCount = 5;
    private boolean newAddressReview = true;
    private long largeAmountThreshold = 50000L;
}
