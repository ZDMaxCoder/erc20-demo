package com.erc20.platform.blockchain.gas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthFeeHistory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EIP1559GasStrategyTest {

    @Mock
    private Web3j web3j;

    @Mock
    @SuppressWarnings("rawtypes")
    private Request feeHistoryRequest;

    private GasProperties gasProperties;
    private EIP1559GasStrategy strategy;

    private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

    @BeforeEach
    void setUp() {
        gasProperties = new GasProperties();
        strategy = new EIP1559GasStrategy(web3j, gasProperties);
    }

    @SuppressWarnings("unchecked")
    private void mockFeeHistory(BigInteger baseFee, BigInteger[] priorityFees) throws Exception {
        EthFeeHistory response = mock(EthFeeHistory.class);
        EthFeeHistory.FeeHistory feeHistory = mock(EthFeeHistory.FeeHistory.class);

        List<BigInteger> baseFeeList = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            baseFeeList.add(baseFee);
        }

        List<List<BigInteger>> rewardList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rewardList.add(Arrays.asList(priorityFees));
        }

        when(feeHistory.getBaseFeePerGas()).thenReturn(baseFeeList);
        when(feeHistory.getReward()).thenReturn(rewardList);
        when(response.getFeeHistory()).thenReturn(feeHistory);
        when(feeHistoryRequest.send()).thenReturn(response);
        when(web3j.ethFeeHistory(anyInt(), any(DefaultBlockParameterName.class), anyList()))
                .thenReturn(feeHistoryRequest);
    }

    @Test
    void getGasPrice_priorityOrdering_lowLessThanMediumLessThanHighLessThanUrgent() throws Exception {
        mockFeeHistory(
                BigInteger.valueOf(20).multiply(GWEI),
                new BigInteger[]{
                        GWEI,
                        BigInteger.valueOf(2).multiply(GWEI),
                        BigInteger.valueOf(3).multiply(GWEI),
                        BigInteger.valueOf(5).multiply(GWEI)
                }
        );

        GasPrice low = strategy.getGasPrice(GasPriority.LOW);
        GasPrice medium = strategy.getGasPrice(GasPriority.MEDIUM);
        GasPrice high = strategy.getGasPrice(GasPriority.HIGH);
        GasPrice urgent = strategy.getGasPrice(GasPriority.URGENT);

        assertTrue(low.isEip1559());
        assertTrue(low.getMaxFeePerGas().compareTo(medium.getMaxFeePerGas()) < 0);
        assertTrue(medium.getMaxFeePerGas().compareTo(high.getMaxFeePerGas()) < 0);
        assertTrue(high.getMaxFeePerGas().compareTo(urgent.getMaxFeePerGas()) < 0);

        assertTrue(low.getMaxPriorityFeePerGas().compareTo(medium.getMaxPriorityFeePerGas()) < 0);
        assertTrue(medium.getMaxPriorityFeePerGas().compareTo(high.getMaxPriorityFeePerGas()) < 0);
        assertTrue(high.getMaxPriorityFeePerGas().compareTo(urgent.getMaxPriorityFeePerGas()) < 0);
    }

    @Test
    void getReplacementGasPrice_atLeast10PercentIncrease() {
        GasPrice original = GasPrice.builder()
                .eip1559(true)
                .maxFeePerGas(BigInteger.valueOf(10).multiply(GWEI))
                .maxPriorityFeePerGas(BigInteger.valueOf(2).multiply(GWEI))
                .build();

        GasPrice replacement = strategy.getReplacementGasPrice(original);

        assertTrue(replacement.isEip1559());
        BigInteger minMaxFee = original.getMaxFeePerGas()
                .multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100));
        BigInteger minPriorityFee = original.getMaxPriorityFeePerGas()
                .multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100));

        assertTrue(replacement.getMaxFeePerGas().compareTo(minMaxFee) >= 0,
                "maxFeePerGas should increase by at least 10%");
        assertTrue(replacement.getMaxPriorityFeePerGas().compareTo(minPriorityFee) >= 0,
                "maxPriorityFeePerGas should increase by at least 10%");
    }

    @Test
    void getGasPrice_exceedingMaxGasPrice_cappedToMax() throws Exception {
        gasProperties.setMaxGasPrice(30_000_000_000L);

        mockFeeHistory(
                BigInteger.valueOf(20).multiply(GWEI),
                new BigInteger[]{
                        GWEI,
                        BigInteger.valueOf(2).multiply(GWEI),
                        BigInteger.valueOf(3).multiply(GWEI),
                        BigInteger.valueOf(5).multiply(GWEI)
                }
        );

        GasPrice urgent = strategy.getGasPrice(GasPriority.URGENT);

        BigInteger maxGas = BigInteger.valueOf(gasProperties.getMaxGasPrice());
        assertTrue(urgent.getMaxFeePerGas().compareTo(maxGas) <= 0,
                "maxFeePerGas should be capped to max-gas-price config");
    }

    @SuppressWarnings("unchecked")
    @Test
    void getGasPrice_feeHistoryError_fallbackToDefault() throws Exception {
        when(web3j.ethFeeHistory(anyInt(), any(DefaultBlockParameterName.class), anyList()))
                .thenReturn(feeHistoryRequest);
        when(feeHistoryRequest.send()).thenThrow(new RuntimeException("RPC error"));

        GasPrice price = strategy.getGasPrice(GasPriority.MEDIUM);

        assertNotNull(price);
        assertTrue(price.isEip1559());
        assertTrue(price.getMaxFeePerGas().compareTo(BigInteger.ZERO) > 0);
    }
}
