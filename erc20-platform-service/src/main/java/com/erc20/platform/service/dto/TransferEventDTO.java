package com.erc20.platform.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEventDTO {

    private String contractAddress;
    private String from;
    private String to;
    private BigInteger value;
    private String txHash;
    private long blockNumber;
    private String blockHash;
    private int logIndex;
}
