package com.erc20.platform.blockchain.gas;

import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthFeeHistory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.erc20.platform.blockchain.gas.GasCalculationHelper.*;

@Slf4j
public class EIP1559GasStrategy implements GasStrategy {

    private static final int BLOCK_COUNT = 10;
    private static final List<Double> REWARD_PERCENTILES = Arrays.asList(10.0, 50.0, 75.0, 95.0);

    private static final BigInteger DEFAULT_BASE_FEE = BigInteger.valueOf(30).multiply(ONE_GWEI);
    private static final BigInteger DEFAULT_PRIORITY_FEE = BigInteger.valueOf(2).multiply(ONE_GWEI);

    private final Web3j web3j;
    private final GasProperties gasProperties;

    private volatile GasPrice lastKnownPrice;

    public EIP1559GasStrategy(Web3j web3j, GasProperties gasProperties) {
        this.web3j = web3j;
        this.gasProperties = gasProperties;
    }

    @Override
    public GasPrice getGasPrice(GasPriority priority) {
        try {
            EthFeeHistory response = web3j.ethFeeHistory(
                    BLOCK_COUNT, DefaultBlockParameterName.LATEST, REWARD_PERCENTILES).send();
            EthFeeHistory.FeeHistory feeHistory = response.getFeeHistory();

            List<BigInteger> baseFees = feeHistory.getBaseFeePerGas();
            BigInteger latestBaseFee = baseFees.get(baseFees.size() - 1);

            List<List<BigInteger>> rewards = feeHistory.getReward();
            int percentileIndex = priorityToPercentileIndex(priority);
            BigInteger medianPriorityFee = calculateMedianReward(rewards, percentileIndex);

            BigInteger multipliedBaseFee = applyMultiplier(latestBaseFee, priority);
            BigInteger maxFeePerGas = multipliedBaseFee.add(medianPriorityFee);
            BigInteger maxPriorityFeePerGas = medianPriorityFee;

            maxFeePerGas = applyCap(maxFeePerGas, gasProperties.getMaxGasPrice());
            maxPriorityFeePerGas = applyCap(maxPriorityFeePerGas, gasProperties.getMaxGasPrice());

            GasPrice result = GasPrice.builder()
                    .eip1559(true)
                    .maxFeePerGas(maxFeePerGas)
                    .maxPriorityFeePerGas(maxPriorityFeePerGas)
                    .build();

            lastKnownPrice = result;
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch fee history, using fallback", e);
            return getFallbackPrice();
        }
    }

    @Override
    public GasPrice getReplacementGasPrice(GasPrice original) {
        BigInteger maxFee = bumpForReplacement(
                original.getMaxFeePerGas() != null ? original.getMaxFeePerGas() : original.getGasPrice());
        BigInteger priorityFee = bumpForReplacement(
                original.getMaxPriorityFeePerGas() != null ? original.getMaxPriorityFeePerGas() : ONE_GWEI);

        return GasPrice.builder()
                .eip1559(original.isEip1559())
                .maxFeePerGas(maxFee)
                .maxPriorityFeePerGas(priorityFee)
                .gasPrice(original.isEip1559() ? null : maxFee)
                .build();
    }

    private int priorityToPercentileIndex(GasPriority priority) {
        switch (priority) {
            case LOW:    return 0;
            case MEDIUM: return 1;
            case HIGH:   return 2;
            case URGENT: return 3;
            default:     return 1;
        }
    }

    private BigInteger applyMultiplier(BigInteger baseFee, GasPriority priority) {
        switch (priority) {
            case LOW:    return baseFee.multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100));
            case MEDIUM: return baseFee.multiply(BigInteger.valueOf(125)).divide(BigInteger.valueOf(100));
            case HIGH:   return baseFee.multiply(BigInteger.valueOf(150)).divide(BigInteger.valueOf(100));
            case URGENT: return baseFee.multiply(BigInteger.valueOf(200)).divide(BigInteger.valueOf(100));
            default:     return baseFee;
        }
    }

    private BigInteger calculateMedianReward(List<List<BigInteger>> rewards, int percentileIndex) {
        BigInteger[] values = new BigInteger[rewards.size()];
        for (int i = 0; i < rewards.size(); i++) {
            values[i] = rewards.get(i).get(percentileIndex);
        }
        Arrays.sort(values);
        int mid = values.length / 2;
        if (values.length % 2 == 0) {
            return values[mid - 1].add(values[mid]).divide(BigInteger.valueOf(2));
        }
        return values[mid];
    }

    private GasPrice getFallbackPrice() {
        if (lastKnownPrice != null) {
            return lastKnownPrice;
        }
        return GasPrice.builder()
                .eip1559(true)
                .maxFeePerGas(DEFAULT_BASE_FEE.add(DEFAULT_PRIORITY_FEE))
                .maxPriorityFeePerGas(DEFAULT_PRIORITY_FEE)
                .build();
    }
}
