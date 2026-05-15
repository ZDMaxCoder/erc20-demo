package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AddressStatus;
import com.erc20.platform.common.enums.CollectionTaskStatus;
import com.erc20.platform.dal.mapper.CollectionTaskMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.UserAddressMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.CollectionTask;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.domain.entity.UserAddress;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.service.gateway.CollectionTransactionSender;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionTaskMapper collectionTaskMapper;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private UserAddressMapper userAddressMapper;
    @Mock private WalletConfigMapper walletConfigMapper;
    @Mock private CollectionTransactionSender transactionSender;
    @Mock private GasSupplyService gasSupplyService;
    @Mock private BusinessMetrics businessMetrics;

    private CollectionProperties properties;
    private CollectionService collectionService;

    private static final String HOT_WALLET = "0xhot1234567890abcdef1234567890abcdef12345678";
    private static final String USER_ADDRESS = "0xuser234567890abcdef1234567890abcdef12345678";
    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        properties = new CollectionProperties();
        properties.setTargetAddress(HOT_WALLET);
        properties.setBatchSize(20);
        properties.setMinIntervalHours(4);
        properties.setGasBufferMultiplier(new BigDecimal("1.5"));
        collectionService = new CollectionService(collectionTaskMapper, tokenConfigMapper,
                userAddressMapper, walletConfigMapper, transactionSender, gasSupplyService, properties, businessMetrics);
    }

    private TokenConfig buildTokenConfig() {
        return TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT)
                .decimals(6)
                .amountExponent(2)
                .collectionThreshold(100000000L)
                .enabled(1)
                .build();
    }

    private UserAddress buildUserAddress() {
        return UserAddress.builder()
                .id(10L)
                .userId("user001")
                .address(USER_ADDRESS)
                .tokenId(TOKEN_ID)
                .status(AddressStatus.BOUND.getCode())
                .build();
    }

    private WalletConfig buildHotWallet() {
        return WalletConfig.builder()
                .id(1L)
                .address(HOT_WALLET)
                .walletType("HOT")
                .enabled(1)
                .build();
    }

    // --- 4.1 scanForCollection: balance >= threshold and no active task → creates PENDING task ---

    @Test
    void scanForCollection_balanceAboveThreshold_noActiveTask_createsTask() {
        TokenConfig token = buildTokenConfig();
        UserAddress addr = buildUserAddress();
        BigInteger balance = BigInteger.valueOf(200000000L);

        doReturn(Collections.singletonList(addr)).when(userAddressMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(balance).when(transactionSender).getERC20Balance(CONTRACT, USER_ADDRESS);
        // no active task
        doReturn(null).when(collectionTaskMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(collectionTaskMapper).insert(any(CollectionTask.class));

        collectionService.scanForCollection(token);

        ArgumentCaptor<CollectionTask> captor = ArgumentCaptor.forClass(CollectionTask.class);
        verify(collectionTaskMapper).insert(captor.capture());
        CollectionTask task = captor.getValue();
        assertEquals(CollectionTaskStatus.PENDING.getCode(), task.getStatus());
        assertEquals(USER_ADDRESS, task.getFromAddress());
        assertEquals(HOT_WALLET, task.getToAddress());
        assertEquals(TOKEN_ID, task.getTokenId());
    }

    // --- 4.2 balance < threshold → no task created ---

    @Test
    void scanForCollection_balanceBelowThreshold_noTaskCreated() {
        TokenConfig token = buildTokenConfig();
        UserAddress addr = buildUserAddress();
        BigInteger balance = BigInteger.valueOf(50000000L);

        doReturn(Collections.singletonList(addr)).when(userAddressMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(balance).when(transactionSender).getERC20Balance(CONTRACT, USER_ADDRESS);

        collectionService.scanForCollection(token);

        verify(collectionTaskMapper, never()).insert(any());
    }

    // --- 4.3 existing active (non-FAILED) task → no duplicate task ---

    @Test
    void scanForCollection_existingActiveTask_noDuplicate() {
        TokenConfig token = buildTokenConfig();
        UserAddress addr = buildUserAddress();
        BigInteger balance = BigInteger.valueOf(200000000L);

        doReturn(Collections.singletonList(addr)).when(userAddressMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(balance).when(transactionSender).getERC20Balance(CONTRACT, USER_ADDRESS);

        CollectionTask existingTask = CollectionTask.builder()
                .id(1L)
                .status(CollectionTaskStatus.GAS_SUPPLYING.getCode())
                .build();
        doReturn(existingTask).when(collectionTaskMapper).selectOne(any(LambdaQueryWrapper.class));

        collectionService.scanForCollection(token);

        verify(collectionTaskMapper, never()).insert(any());
    }

    // --- 4.4 address collected within minIntervalHours → skipped ---

    @Test
    void scanForCollection_collectedRecently_skipped() {
        TokenConfig token = buildTokenConfig();
        UserAddress addr = buildUserAddress();
        BigInteger balance = BigInteger.valueOf(200000000L);

        doReturn(Collections.singletonList(addr)).when(userAddressMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(balance).when(transactionSender).getERC20Balance(CONTRACT, USER_ADDRESS);
        // no active task
        doReturn(null).when(collectionTaskMapper).selectOne(any(LambdaQueryWrapper.class));

        // recent successful collection
        CollectionTask recentTask = CollectionTask.builder()
                .id(2L)
                .status(CollectionTaskStatus.SUCCESS.getCode())
                .createdAt(new Date())
                .build();
        doReturn(Collections.singletonList(recentTask)).when(collectionTaskMapper).selectList(any(LambdaQueryWrapper.class));

        collectionService.scanForCollection(token);

        verify(collectionTaskMapper, never()).insert(any());
    }

    // --- 4.5 executeCollection with sufficient ETH → skips gas supply, directly sends ERC-20 transfer ---

    @Test
    void executeCollection_sufficientEth_directTransfer_success() {
        CollectionTask task = CollectionTask.builder()
                .id(1L)
                .fromAddress(USER_ADDRESS)
                .toAddress(HOT_WALLET)
                .tokenId(TOKEN_ID)
                .amount(200000000L)
                .amountExponent(2)
                .status(CollectionTaskStatus.PENDING.getCode())
                .retryCount(0)
                .build();

        TokenConfig token = buildTokenConfig();
        doReturn(token).when(tokenConfigMapper).selectById(TOKEN_ID);

        BigInteger gasRequired = BigInteger.valueOf(1_000_000_000_000_000L);
        BigInteger ethBalance = BigInteger.valueOf(2_000_000_000_000_000L);
        doReturn(gasRequired).when(gasSupplyService)
                .estimateRequiredGas(eq(CONTRACT), eq(USER_ADDRESS), eq(HOT_WALLET), any(BigInteger.class));
        doReturn(ethBalance).when(transactionSender).getEthBalance(USER_ADDRESS);

        TransactionRecord txRecord = TransactionRecord.builder()
                .txHash("0xtx_collect")
                .build();
        doReturn(txRecord).when(transactionSender)
                .sendERC20Transfer(eq(USER_ADDRESS), eq(HOT_WALLET), eq(CONTRACT), any(BigInteger.class));
        doReturn(1).when(collectionTaskMapper).updateById(any(CollectionTask.class));

        collectionService.executeCollection(task);

        verify(gasSupplyService, never()).supplyGas(anyString(), anyString(), anyString(), any(BigInteger.class));
        verify(transactionSender).sendERC20Transfer(eq(USER_ADDRESS), eq(HOT_WALLET), eq(CONTRACT), any(BigInteger.class));

        ArgumentCaptor<CollectionTask> captor = ArgumentCaptor.forClass(CollectionTask.class);
        verify(collectionTaskMapper, atLeastOnce()).updateById(captor.capture());
        CollectionTask updated = captor.getValue();
        assertEquals(CollectionTaskStatus.COLLECTING.getCode(), updated.getStatus());
        assertEquals("0xtx_collect", updated.getTxHash());
    }

    // --- 4.6 executeCollection with insufficient ETH → GAS_SUPPLYING state, gas supply tx sent ---

    @Test
    void executeCollection_insufficientEth_gasSupplyTriggered() {
        CollectionTask task = CollectionTask.builder()
                .id(1L)
                .fromAddress(USER_ADDRESS)
                .toAddress(HOT_WALLET)
                .tokenId(TOKEN_ID)
                .amount(200000000L)
                .amountExponent(2)
                .status(CollectionTaskStatus.PENDING.getCode())
                .retryCount(0)
                .build();

        TokenConfig token = buildTokenConfig();
        doReturn(token).when(tokenConfigMapper).selectById(TOKEN_ID);

        BigInteger gasRequired = BigInteger.valueOf(2_000_000_000_000_000L);
        BigInteger ethBalance = BigInteger.valueOf(100_000L);
        doReturn(gasRequired).when(gasSupplyService)
                .estimateRequiredGas(eq(CONTRACT), eq(USER_ADDRESS), eq(HOT_WALLET), any(BigInteger.class));
        doReturn(ethBalance).when(transactionSender).getEthBalance(USER_ADDRESS);

        TransactionRecord gasRecord = TransactionRecord.builder()
                .txHash("0xtx_gas")
                .build();
        doReturn(gasRecord).when(gasSupplyService)
                .supplyGas(eq(HOT_WALLET), eq(USER_ADDRESS), eq(CONTRACT), any(BigInteger.class));
        doReturn(1).when(collectionTaskMapper).updateById(any(CollectionTask.class));

        WalletConfig hotWallet = buildHotWallet();
        doReturn(hotWallet).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        collectionService.executeCollection(task);

        verify(gasSupplyService).supplyGas(eq(HOT_WALLET), eq(USER_ADDRESS), eq(CONTRACT), any(BigInteger.class));
        verify(transactionSender, never()).sendERC20Transfer(anyString(), anyString(), anyString(), any(BigInteger.class));

        ArgumentCaptor<CollectionTask> captor = ArgumentCaptor.forClass(CollectionTask.class);
        verify(collectionTaskMapper, atLeastOnce()).updateById(captor.capture());
        CollectionTask updated = captor.getValue();
        assertEquals(CollectionTaskStatus.GAS_SUPPLYING.getCode(), updated.getStatus());
        assertEquals("0xtx_gas", updated.getGasTxHash());
    }
}
