package com.erc20.platform.service;

import com.erc20.platform.dal.mapper.AlertRecordMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.AlertRecord;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.service.gateway.CollectionTransactionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasSupplyServiceTest {

    @Mock private CollectionTransactionSender transactionSender;
    @Mock private WalletConfigMapper walletConfigMapper;
    @Mock private AlertRecordMapper alertRecordMapper;

    private CollectionProperties properties;
    private GasSupplyService gasSupplyService;

    private static final String HOT_WALLET = "0xhot1234567890abcdef1234567890abcdef12345678";
    private static final String USER_ADDRESS = "0xuser234567890abcdef1234567890abcdef12345678";
    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    @BeforeEach
    void setUp() {
        properties = new CollectionProperties();
        properties.setGasBufferMultiplier(new BigDecimal("1.5"));
        gasSupplyService = new GasSupplyService(transactionSender, walletConfigMapper,
                alertRecordMapper, properties);
    }

    @Test
    void estimateRequiredGas_returnsGasCostTimesBufferMultiplier() {
        BigInteger baseCost = BigInteger.valueOf(65000L * 20_000_000_000L);
        doReturn(baseCost).when(transactionSender)
                .estimateGasCost(eq(CONTRACT), eq(USER_ADDRESS), eq(HOT_WALLET), any(BigInteger.class));

        BigInteger result = gasSupplyService.estimateRequiredGas(CONTRACT, USER_ADDRESS, HOT_WALLET,
                BigInteger.valueOf(100000000));

        BigInteger expected = new BigDecimal(baseCost).multiply(new BigDecimal("1.5")).toBigInteger();
        assertEquals(expected, result);
    }

    @Test
    void supplyGas_sufficientBalance_sendsEthTransferAndReturnsRecord() {
        BigInteger gasCost = BigInteger.valueOf(1_950_000_000_000_000L);
        BigInteger hotBalance = BigInteger.valueOf(10_000_000_000_000_000L);

        doReturn(gasCost).when(transactionSender)
                .estimateGasCost(eq(CONTRACT), eq(USER_ADDRESS), eq(HOT_WALLET), any(BigInteger.class));
        doReturn(hotBalance).when(transactionSender).getEthBalance(HOT_WALLET);

        TransactionRecord expected = TransactionRecord.builder()
                .txHash("0xtx_gas_supply")
                .build();
        doReturn(expected).when(transactionSender)
                .sendEthTransfer(eq(HOT_WALLET), eq(USER_ADDRESS), any(BigInteger.class));

        TransactionRecord result = gasSupplyService.supplyGas(HOT_WALLET, USER_ADDRESS, CONTRACT,
                BigInteger.valueOf(100000000));

        assertNotNull(result);
        assertEquals("0xtx_gas_supply", result.getTxHash());
        verify(transactionSender).sendEthTransfer(eq(HOT_WALLET), eq(USER_ADDRESS), any(BigInteger.class));
    }

    @Test
    void supplyGas_insufficientBalance_alertsAndReturnsNull() {
        BigInteger gasCost = BigInteger.valueOf(1_950_000_000_000_000L);
        BigInteger hotBalance = BigInteger.valueOf(100_000L);

        doReturn(gasCost).when(transactionSender)
                .estimateGasCost(eq(CONTRACT), eq(USER_ADDRESS), eq(HOT_WALLET), any(BigInteger.class));
        doReturn(hotBalance).when(transactionSender).getEthBalance(HOT_WALLET);
        doReturn(1).when(alertRecordMapper).insert(any(AlertRecord.class));

        TransactionRecord result = gasSupplyService.supplyGas(HOT_WALLET, USER_ADDRESS, CONTRACT,
                BigInteger.valueOf(100000000));

        assertNull(result);
        verify(transactionSender, never()).sendEthTransfer(anyString(), anyString(), any(BigInteger.class));

        ArgumentCaptor<AlertRecord> captor = ArgumentCaptor.forClass(AlertRecord.class);
        verify(alertRecordMapper).insert(captor.capture());
        assertEquals("WARN", captor.getValue().getAlertLevel());
    }
}
