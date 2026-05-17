package com.erc20.platform.blockchain.wallet;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class TxStatusChangedMessage {

    private String txHash;
    private String fromStatus;
    private String toStatus;
    private Long blockNumber;
    private String blockHash;
    private BigInteger actualAmount;
    private boolean anomaly;
    private String anomalyReason;
}
