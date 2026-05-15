package com.erc20.platform.blockchain.gas;

import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.Web3j;

import java.math.BigInteger;

import static com.erc20.platform.blockchain.gas.GasCalculationHelper.*;

@Slf4j
public class LegacyGasStrategy implements GasStrategy {

    private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(20).multiply(ONE_GWEI);

    private final Web3j web3j;
    private final GasProperties gasProperties;

    private volatile GasPrice lastKnownPrice;

    public LegacyGasStrategy(Web3j web3j, GasProperties gasProperties) {
        this.web3j = web3j;
        this.gasProperties = gasProperties;
    }

    @Override
    public GasPrice getGasPrice(GasPriority priority) {
        try {
            BigInteger suggested = web3j.ethGasPrice().send().getGasPrice();
            BigInteger adjusted = applyMultiplier(suggested, priority);
            adjusted = applyCap(adjusted, gasProperties.getMaxGasPrice());

            GasPrice result = GasPrice.builder()
                    .eip1559(false)
                    .gasPrice(adjusted)
                    .build();

            lastKnownPrice = result;
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch gas price, using fallback", e);
            return getFallbackPrice();
        }
    }

    @Override
    public GasPrice getReplacementGasPrice(GasPrice original) {
        BigInteger replacement = bumpForReplacement(original.getGasPrice());

        return GasPrice.builder()
                .eip1559(false)
                .gasPrice(replacement)
                .build();
    }

    private BigInteger applyMultiplier(BigInteger suggested, GasPriority priority) {
        switch (priority) {
            case LOW:    return suggested.multiply(BigInteger.valueOf(90)).divide(BigInteger.valueOf(100));
            case MEDIUM: return suggested;
            case HIGH:   return suggested.multiply(BigInteger.valueOf(130)).divide(BigInteger.valueOf(100));
            case URGENT: return suggested.multiply(BigInteger.valueOf(180)).divide(BigInteger.valueOf(100));
            default:     return suggested;
        }
    }

    private GasPrice getFallbackPrice() {
        if (lastKnownPrice != null) {
            return lastKnownPrice;
        }
        return GasPrice.builder()
                .eip1559(false)
                .gasPrice(DEFAULT_GAS_PRICE)
                .build();
    }
}
