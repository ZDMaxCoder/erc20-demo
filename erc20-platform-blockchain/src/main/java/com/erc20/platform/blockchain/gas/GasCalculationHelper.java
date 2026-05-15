package com.erc20.platform.blockchain.gas;

import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;

@Slf4j
final class GasCalculationHelper {

    static final BigInteger ONE_GWEI = BigInteger.valueOf(1_000_000_000L);

    private GasCalculationHelper() {
    }

    static BigInteger applyCap(BigInteger value, long maxGasPrice) {
        BigInteger cap = BigInteger.valueOf(maxGasPrice);
        if (value.compareTo(cap) > 0) {
            log.warn("Gas price {} exceeds max cap {}, clamping", value, cap);
            return cap;
        }
        return value;
    }

    static BigInteger bumpForReplacement(BigInteger original) {
        BigInteger bumped115 = original.multiply(BigInteger.valueOf(115)).divide(BigInteger.valueOf(100));
        BigInteger bumpedPlus1Gwei = original.add(ONE_GWEI);
        return bumped115.compareTo(bumpedPlus1Gwei) >= 0 ? bumped115 : bumpedPlus1Gwei;
    }
}
