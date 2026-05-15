package com.erc20.platform.blockchain.gas;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;

@Data
@Builder
public class GasPrice {

    private BigInteger gasPrice;

    private BigInteger maxFeePerGas;

    private BigInteger maxPriorityFeePerGas;

    private boolean eip1559;
}
