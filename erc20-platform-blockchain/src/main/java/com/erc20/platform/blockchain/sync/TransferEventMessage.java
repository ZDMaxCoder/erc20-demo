package com.erc20.platform.blockchain.sync;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class TransferEventMessage {

    private String contractAddress;
    private String from;
    private String to;
    private BigInteger value;
    private String txHash;
    private long blockNumber;
    private int logIndex;
}
