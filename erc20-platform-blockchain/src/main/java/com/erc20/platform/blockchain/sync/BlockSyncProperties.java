package com.erc20.platform.blockchain.sync;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "blockchain.sync")
public class BlockSyncProperties {

    private int chainId = 1;
    private String startBlock = "latest";
    private int batchSize = 5;
    private long pollInterval = 3000;
    private int maxReorgDepth = 50;
    private String rpcUrl = "http://localhost:8545";
    private String backupRpcUrl;
}
