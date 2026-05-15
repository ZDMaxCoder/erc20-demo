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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Slf4j
@Service
public class CollectionTriggerService {

    private final CollectionTaskMapper collectionTaskMapper;
    private final TokenConfigMapper tokenConfigMapper;
    private final CollectionTransactionSender transactionSender;
    private final CollectionService collectionService;
    private final CollectionProperties properties;

    public CollectionTriggerService(CollectionTaskMapper collectionTaskMapper,
                                    TokenConfigMapper tokenConfigMapper,
                                    CollectionTransactionSender transactionSender,
                                    CollectionService collectionService,
                                    CollectionProperties properties) {
        this.collectionTaskMapper = collectionTaskMapper;
        this.tokenConfigMapper = tokenConfigMapper;
        this.transactionSender = transactionSender;
        this.collectionService = collectionService;
        this.properties = properties;
    }

    public void onDepositConfirmed(DepositConfirmedMessage message) {
        if (!properties.isEnabled()) {
            return;
        }

        TokenConfig token = tokenConfigMapper.selectById(message.getTokenId());
        if (token == null || token.getCollectionThreshold() == null) {
            return;
        }

        BigInteger balance = transactionSender.getERC20Balance(
                token.getContractAddress(), message.getAddress());

        if (balance.compareTo(BigInteger.valueOf(token.getCollectionThreshold())) < 0) {
            log.debug("Address {} balance {} below threshold {}, skipping collection",
                    message.getAddress(), balance, token.getCollectionThreshold());
            return;
        }

        collectionService.scanForCollection(token);
    }

    public void onTxStatusChanged(String txHash, String toStatus) {
        CollectionTask gasTask = collectionTaskMapper.selectOne(
                new LambdaQueryWrapper<CollectionTask>()
                        .eq(CollectionTask::getGasTxHash, txHash)
                        .eq(CollectionTask::getStatus, CollectionTaskStatus.GAS_SUPPLYING.getCode()));

        if (gasTask != null) {
            handleGasSupplyResult(gasTask, toStatus);
            return;
        }

        CollectionTask collectTask = collectionTaskMapper.selectOne(
                new LambdaQueryWrapper<CollectionTask>()
                        .eq(CollectionTask::getTxHash, txHash)
                        .eq(CollectionTask::getStatus, CollectionTaskStatus.COLLECTING.getCode()));

        if (collectTask != null) {
            handleCollectionResult(collectTask, toStatus);
        }
    }

    private void handleGasSupplyResult(CollectionTask task, String toStatus) {
        if (TxStatus.CONFIRMED.getCode().equals(toStatus)) {
            collectionService.advanceGasConfirmed(task);
        } else if (TxStatus.FAILED.getCode().equals(toStatus)) {
            collectionService.markFailed(task, "Gas supply transaction failed");
        }
    }

    private void handleCollectionResult(CollectionTask task, String toStatus) {
        if (TxStatus.CONFIRMED.getCode().equals(toStatus)) {
            collectionService.markSuccess(task);
        } else if (TxStatus.FAILED.getCode().equals(toStatus)) {
            collectionService.markFailed(task, "Collection transaction failed");
        }
    }
}
