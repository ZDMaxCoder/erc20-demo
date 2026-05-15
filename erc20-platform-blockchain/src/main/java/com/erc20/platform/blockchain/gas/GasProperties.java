package com.erc20.platform.blockchain.gas;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "blockchain.gas")
public class GasProperties {

    private String strategy = "eip1559";

    private long maxGasPrice = 100_000_000_000L;

    private int stuckTimeoutMinutes = 5;

    private int maxReplacementCount = 3;

    private int gasLimitBufferPercent = 20;
}
