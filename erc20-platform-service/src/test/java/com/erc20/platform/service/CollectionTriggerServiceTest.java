package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.CollectionTaskStatus;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.CollectionTaskMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.CollectionTask;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.service.dto.DepositConfirmedMessage;
import com.erc20.platform.service.gateway.CollectionTransactionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionTriggerServiceTest {

    @Mock private CollectionTaskMapper collectionTaskMapper;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private CollectionTransactionSender transactionSender;
    @Mock private CollectionService collectionService;

    private CollectionProperties properties;
    private CollectionTriggerService triggerService;

    private static final String USER_ADDRESS = "0xuser234567890abcdef1234567890abcdef12345678";
    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        properties = new CollectionProperties();
        properties.setEnabled(true);
        triggerService = new CollectionTriggerService(collectionTaskMapper, tokenConfigMapper,
                transactionSender, collectionService, properties);
    }

    private TokenConfig buildTokenConfig() {
        return TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT)
                .collectionThreshold(100000000L)
                .enabled(1)
                .build();
    }

    @Test
    void onDepositConfirmed_balanceAboveThreshold_triggersCollectionScan() {
        DepositConfirmedMessage message = DepositConfirmedMessage.builder()
                .depositId(1L)
                .userId("user001")
                .tokenId(TOKEN_ID)
                .address(USER_ADDRESS)
                .amount(200000000L)
                .build();

        TokenConfig token = buildTokenConfig();
        doReturn(token).when(tokenConfigMapper).selectById(TOKEN_ID);

        BigInteger balance = BigInteger.valueOf(200000000L);
        doReturn(balance).when(transactionSender).getERC20Balance(CONTRACT, USER_ADDRESS);

        triggerService.onDepositConfirmed(message);

        verify(collectionService).scanForCollection(token);
    }

    @Test
    void onDepositConfirmed_balanceBelowThreshold_noAction() {
        DepositConfirmedMessage message = DepositConfirmedMessage.builder()
                .depositId(1L)
                .userId("user001")
                .tokenId(TOKEN_ID)
                .address(USER_ADDRESS)
                .amount(50000000L)
                .build();

        TokenConfig token = buildTokenConfig();
        doReturn(token).when(tokenConfigMapper).selectById(TOKEN_ID);

        BigInteger balance = BigInteger.valueOf(50000000L);
        doReturn(balance).when(transactionSender).getERC20Balance(CONTRACT, USER_ADDRESS);

        triggerService.onDepositConfirmed(message);

        verify(collectionService, never()).scanForCollection(any(TokenConfig.class));
    }

    @Test
    void onTxStatusChanged_gasSupplyConfirmed_advancesTask() {
        CollectionTask task = CollectionTask.builder()
                .id(1L)
                .fromAddress(USER_ADDRESS)
                .status(CollectionTaskStatus.GAS_SUPPLYING.getCode())
                .gasTxHash("0xtx_gas")
                .build();

        doReturn(task).when(collectionTaskMapper).selectOne(any(LambdaQueryWrapper.class));

        triggerService.onTxStatusChanged("0xtx_gas", TxStatus.CONFIRMED.getCode());

        verify(collectionService).advanceGasConfirmed(task);
    }

    @Test
    void onTxStatusChanged_collectionConfirmed_marksSuccess() {
        CollectionTask task = CollectionTask.builder()
                .id(1L)
                .fromAddress(USER_ADDRESS)
                .status(CollectionTaskStatus.COLLECTING.getCode())
                .txHash("0xtx_collect")
                .build();

        doReturn(null).doReturn(task).when(collectionTaskMapper).selectOne(any(LambdaQueryWrapper.class));

        triggerService.onTxStatusChanged("0xtx_collect", TxStatus.CONFIRMED.getCode());

        verify(collectionService).markSuccess(task);
    }

    @Test
    void onTxStatusChanged_txFailed_marksTaskFailed() {
        CollectionTask task = CollectionTask.builder()
                .id(1L)
                .fromAddress(USER_ADDRESS)
                .status(CollectionTaskStatus.GAS_SUPPLYING.getCode())
                .gasTxHash("0xtx_gas")
                .build();

        doReturn(task).when(collectionTaskMapper).selectOne(any(LambdaQueryWrapper.class));

        triggerService.onTxStatusChanged("0xtx_gas", TxStatus.FAILED.getCode());

        verify(collectionService).markFailed(eq(task), anyString());
    }
}
