package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.gas.GasPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.RawTransaction;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class TransactionBuilderTest {

    private TransactionBuilder transactionBuilder;

    private static final String CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String RECIPIENT = "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final BigInteger AMOUNT = BigInteger.valueOf(1000000);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(80000);
    private static final long NONCE = 5L;

    @BeforeEach
    void setUp() {
        transactionBuilder = new TransactionBuilder();
    }

    // --- 1.1 buildERC20Transfer encodes correct function selector + ABI-encoded address + amount ---

    @Test
    void buildERC20Transfer_legacy_encodesCorrectFunctionSelectorAndData() {
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();

        RawTransaction rawTx = transactionBuilder.buildERC20Transfer(
                NONCE, gasPrice, GAS_LIMIT, CONTRACT, RECIPIENT, AMOUNT);

        assertNotNull(rawTx);
        assertEquals(CONTRACT.toLowerCase(), rawTx.getTo().toLowerCase());
        assertEquals(BigInteger.ZERO, rawTx.getValue());

        String data = Numeric.cleanHexPrefix(rawTx.getData()).toLowerCase();
        assertTrue(data.startsWith("a9059cbb"), "Should start with transfer function selector a9059cbb, got: " + data.substring(0, Math.min(8, data.length())));

        String addressParam = data.substring(8, 72);
        assertTrue(addressParam.endsWith(RECIPIENT.substring(2).toLowerCase()),
                "ABI-encoded address should contain recipient");

        String amountParam = data.substring(72, 136);
        BigInteger decodedAmount = new BigInteger(amountParam, 16);
        assertEquals(AMOUNT, decodedAmount);
    }

    // --- 1.2 buildEthTransfer creates correct RawTransaction ---

    @Test
    void buildEthTransfer_legacy_createsCorrectTransaction() {
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();
        BigInteger amountWei = BigInteger.valueOf(1_000_000_000_000_000_000L);

        RawTransaction rawTx = transactionBuilder.buildEthTransfer(
                NONCE, gasPrice, GAS_LIMIT, RECIPIENT, amountWei);

        assertNotNull(rawTx);
        assertEquals(RECIPIENT.toLowerCase(), rawTx.getTo().toLowerCase());
        assertEquals(amountWei, rawTx.getValue());
        assertTrue(rawTx.getData() == null || rawTx.getData().isEmpty() || rawTx.getData().equals("0x"));
    }

    // --- 1.3 EIP-1559 transaction type selected when GasPrice.isEip1559()==true ---

    @Test
    void buildERC20Transfer_eip1559_createsEip1559Transaction() {
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(true)
                .maxFeePerGas(BigInteger.valueOf(30_000_000_000L))
                .maxPriorityFeePerGas(BigInteger.valueOf(2_000_000_000L))
                .build();

        RawTransaction rawTx = transactionBuilder.buildERC20Transfer(
                NONCE, gasPrice, GAS_LIMIT, CONTRACT, RECIPIENT, AMOUNT);

        assertNotNull(rawTx);
        assertNotNull(rawTx.getTransaction());
        assertEquals(CONTRACT.toLowerCase(), rawTx.getTo().toLowerCase());
    }

    @Test
    void buildEthTransfer_eip1559_createsEip1559Transaction() {
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(true)
                .maxFeePerGas(BigInteger.valueOf(30_000_000_000L))
                .maxPriorityFeePerGas(BigInteger.valueOf(2_000_000_000L))
                .build();
        BigInteger amountWei = BigInteger.valueOf(1_000_000_000_000_000_000L);

        RawTransaction rawTx = transactionBuilder.buildEthTransfer(
                NONCE, gasPrice, GAS_LIMIT, RECIPIENT, amountWei);

        assertNotNull(rawTx);
        assertEquals(RECIPIENT.toLowerCase(), rawTx.getTo().toLowerCase());
        assertEquals(amountWei, rawTx.getValue());
    }
}
