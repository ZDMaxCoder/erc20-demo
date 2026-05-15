package com.erc20.platform.blockchain.gas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGasPrice;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyGasStrategyTest {

    @Mock
    private Web3j web3j;

    @Mock
    @SuppressWarnings("rawtypes")
    private Request gasPriceRequest;

    private GasProperties gasProperties;
    private LegacyGasStrategy strategy;

    private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

    @BeforeEach
    void setUp() {
        gasProperties = new GasProperties();
        strategy = new LegacyGasStrategy(web3j, gasProperties);
    }

    @SuppressWarnings("unchecked")
    private void mockGasPrice(BigInteger suggested) throws Exception {
        EthGasPrice response = mock(EthGasPrice.class);
        when(response.getGasPrice()).thenReturn(suggested);
        when(gasPriceRequest.send()).thenReturn(response);
        when(web3j.ethGasPrice()).thenReturn(gasPriceRequest);
    }

    @Test
    void getGasPrice_multipliers_lowLessThanMediumLessThanHighLessThanUrgent() throws Exception {
        BigInteger suggested = BigInteger.valueOf(20).multiply(GWEI);
        mockGasPrice(suggested);

        GasPrice low = strategy.getGasPrice(GasPriority.LOW);
        GasPrice medium = strategy.getGasPrice(GasPriority.MEDIUM);
        GasPrice high = strategy.getGasPrice(GasPriority.HIGH);
        GasPrice urgent = strategy.getGasPrice(GasPriority.URGENT);

        assertFalse(low.isEip1559());

        // LOW = suggested * 0.9 = 18 Gwei
        BigInteger expectedLow = suggested.multiply(BigInteger.valueOf(90)).divide(BigInteger.valueOf(100));
        assertEquals(expectedLow, low.getGasPrice());

        // MEDIUM = suggested * 1.0 = 20 Gwei
        assertEquals(suggested, medium.getGasPrice());

        // HIGH = suggested * 1.3 = 26 Gwei
        BigInteger expectedHigh = suggested.multiply(BigInteger.valueOf(130)).divide(BigInteger.valueOf(100));
        assertEquals(expectedHigh, high.getGasPrice());

        // URGENT = suggested * 1.8 = 36 Gwei
        BigInteger expectedUrgent = suggested.multiply(BigInteger.valueOf(180)).divide(BigInteger.valueOf(100));
        assertEquals(expectedUrgent, urgent.getGasPrice());

        assertTrue(low.getGasPrice().compareTo(medium.getGasPrice()) < 0);
        assertTrue(medium.getGasPrice().compareTo(high.getGasPrice()) < 0);
        assertTrue(high.getGasPrice().compareTo(urgent.getGasPrice()) < 0);
    }

    @Test
    void getReplacementGasPrice_atLeast10PercentIncrease() {
        GasPrice original = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(10).multiply(GWEI))
                .build();

        GasPrice replacement = strategy.getReplacementGasPrice(original);

        assertFalse(replacement.isEip1559());
        BigInteger minRequired = original.getGasPrice()
                .multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100));
        assertTrue(replacement.getGasPrice().compareTo(minRequired) >= 0,
                "replacement gasPrice should increase by at least 10%");
    }

    @Test
    void getGasPrice_exceedingMaxGasPrice_cappedToMax() throws Exception {
        gasProperties.setMaxGasPrice(25_000_000_000L); // 25 Gwei cap
        BigInteger suggested = BigInteger.valueOf(20).multiply(GWEI);
        mockGasPrice(suggested);

        // URGENT = 20 * 1.8 = 36 Gwei, exceeds 25 Gwei cap
        GasPrice urgent = strategy.getGasPrice(GasPriority.URGENT);

        BigInteger maxGas = BigInteger.valueOf(gasProperties.getMaxGasPrice());
        assertTrue(urgent.getGasPrice().compareTo(maxGas) <= 0,
                "gasPrice should be capped to max-gas-price config");
    }
}
