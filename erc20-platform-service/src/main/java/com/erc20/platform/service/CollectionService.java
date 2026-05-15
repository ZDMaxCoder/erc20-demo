package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AddressStatus;
import com.erc20.platform.common.enums.CollectionTaskStatus;
import com.erc20.platform.common.enums.WalletType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class CollectionService {

    private final CollectionTaskMapper collectionTaskMapper;
    private final TokenConfigMapper tokenConfigMapper;
    private final UserAddressMapper userAddressMapper;
    private final WalletConfigMapper walletConfigMapper;
    private final CollectionTransactionSender transactionSender;
    private final GasSupplyService gasSupplyService;
    private final CollectionProperties properties;
    private final BusinessMetrics businessMetrics;

    public CollectionService(CollectionTaskMapper collectionTaskMapper,
                             TokenConfigMapper tokenConfigMapper,
                             UserAddressMapper userAddressMapper,
                             WalletConfigMapper walletConfigMapper,
                             CollectionTransactionSender transactionSender,
                             GasSupplyService gasSupplyService,
                             CollectionProperties properties,
                             BusinessMetrics businessMetrics) {
        this.collectionTaskMapper = collectionTaskMapper;
        this.tokenConfigMapper = tokenConfigMapper;
        this.userAddressMapper = userAddressMapper;
        this.walletConfigMapper = walletConfigMapper;
        this.transactionSender = transactionSender;
        this.gasSupplyService = gasSupplyService;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
    }

    public void scanForCollection(TokenConfig token) {
        List<UserAddress> addresses = userAddressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getTokenId, token.getId())
                        .eq(UserAddress::getStatus, AddressStatus.BOUND.getCode()));

        for (UserAddress addr : addresses) {
            try {
                checkAndCreateTask(addr, token);
            } catch (Exception e) {
                log.error("Failed to check collection for address {}", addr.getAddress(), e);
            }
        }
    }

    private void checkAndCreateTask(UserAddress addr, TokenConfig token) {
        BigInteger balance = transactionSender.getERC20Balance(token.getContractAddress(), addr.getAddress());
        if (balance.compareTo(BigInteger.valueOf(token.getCollectionThreshold())) < 0) {
            return;
        }

        CollectionTask activeTask = collectionTaskMapper.selectOne(
                new LambdaQueryWrapper<CollectionTask>()
                        .eq(CollectionTask::getFromAddress, addr.getAddress())
                        .eq(CollectionTask::getTokenId, token.getId())
                        .notIn(CollectionTask::getStatus,
                                CollectionTaskStatus.SUCCESS.getCode(),
                                CollectionTaskStatus.FAILED.getCode()));
        if (activeTask != null) {
            log.debug("Active collection task already exists for address {}", addr.getAddress());
            return;
        }

        if (hasRecentCollection(addr.getAddress(), token.getId())) {
            log.debug("Address {} collected within {} hours, skipping", addr.getAddress(),
                    properties.getMinIntervalHours());
            return;
        }

        CollectionTask task = CollectionTask.builder()
                .fromAddress(addr.getAddress())
                .toAddress(properties.getTargetAddress())
                .tokenId(token.getId())
                .amount(balance.longValue())
                .amountExponent(token.getAmountExponent())
                .status(CollectionTaskStatus.PENDING.getCode())
                .retryCount(0)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        collectionTaskMapper.insert(task);
        log.info("Collection task created for address {}, amount {}", addr.getAddress(), balance);
    }

    private boolean hasRecentCollection(String fromAddress, Long tokenId) {
        long cutoffTime = System.currentTimeMillis() - properties.getMinIntervalHours() * 3600_000L;
        Date cutoff = new Date(cutoffTime);

        List<CollectionTask> recent = collectionTaskMapper.selectList(
                new LambdaQueryWrapper<CollectionTask>()
                        .eq(CollectionTask::getFromAddress, fromAddress)
                        .eq(CollectionTask::getTokenId, tokenId)
                        .eq(CollectionTask::getStatus, CollectionTaskStatus.SUCCESS.getCode())
                        .ge(CollectionTask::getCreatedAt, cutoff));
        return !recent.isEmpty();
    }

    public void executeCollection(CollectionTask task) {
        TokenConfig token = tokenConfigMapper.selectById(task.getTokenId());
        BigInteger amount = BigInteger.valueOf(task.getAmount());

        BigInteger gasRequired = gasSupplyService.estimateRequiredGas(
                token.getContractAddress(), task.getFromAddress(), task.getToAddress(), amount);
        BigInteger ethBalance = transactionSender.getEthBalance(task.getFromAddress());

        if (ethBalance.compareTo(gasRequired) >= 0) {
            sendCollectionTransfer(task, token, amount);
        } else {
            supplyGasFirst(task, token, amount);
        }
    }

    private void sendCollectionTransfer(CollectionTask task, TokenConfig token, BigInteger amount) {
        task.setStatus(CollectionTaskStatus.COLLECTING.getCode());
        task.setUpdatedAt(new Date());

        TransactionRecord txRecord = transactionSender.sendERC20Transfer(
                task.getFromAddress(), task.getToAddress(), token.getContractAddress(), amount);
        task.setTxHash(txRecord.getTxHash());
        collectionTaskMapper.updateById(task);
        log.info("Collection transfer sent for task {}: txHash={}", task.getId(), txRecord.getTxHash());
    }

    private void supplyGasFirst(CollectionTask task, TokenConfig token, BigInteger amount) {
        WalletConfig hotWallet = walletConfigMapper.selectOne(
                new LambdaQueryWrapper<WalletConfig>()
                        .eq(WalletConfig::getWalletType, WalletType.HOT.getCode())
                        .eq(WalletConfig::getEnabled, 1));

        TransactionRecord gasRecord = gasSupplyService.supplyGas(
                hotWallet.getAddress(), task.getFromAddress(),
                token.getContractAddress(), amount);

        if (gasRecord == null) {
            task.setStatus(CollectionTaskStatus.FAILED.getCode());
            task.setErrorMessage("Gas supply failed: hot wallet insufficient");
            task.setUpdatedAt(new Date());
            collectionTaskMapper.updateById(task);
            return;
        }

        task.setStatus(CollectionTaskStatus.GAS_SUPPLYING.getCode());
        task.setGasTxHash(gasRecord.getTxHash());
        task.setUpdatedAt(new Date());
        collectionTaskMapper.updateById(task);
        log.info("Gas supply sent for task {}: gasTxHash={}", task.getId(), gasRecord.getTxHash());
    }

    public void batchCollection() {
        List<CollectionTask> pendingTasks = collectionTaskMapper.selectList(
                new LambdaQueryWrapper<CollectionTask>()
                        .eq(CollectionTask::getStatus, CollectionTaskStatus.PENDING.getCode())
                        .last("LIMIT " + properties.getBatchSize()));

        Semaphore semaphore = new Semaphore(properties.getBatchSize());
        for (CollectionTask task : pendingTasks) {
            if (!semaphore.tryAcquire()) {
                break;
            }
            try {
                executeCollection(task);
            } catch (Exception e) {
                log.error("Failed to execute collection task {}", task.getId(), e);
                task.setStatus(CollectionTaskStatus.FAILED.getCode());
                task.setErrorMessage(e.getMessage());
                task.setUpdatedAt(new Date());
                collectionTaskMapper.updateById(task);
            } finally {
                semaphore.release();
            }
        }
    }

    public void advanceGasConfirmed(CollectionTask task) {
        TokenConfig token = tokenConfigMapper.selectById(task.getTokenId());
        BigInteger amount = BigInteger.valueOf(task.getAmount());

        task.setStatus(CollectionTaskStatus.GAS_CONFIRMED.getCode());
        task.setUpdatedAt(new Date());
        collectionTaskMapper.updateById(task);

        sendCollectionTransfer(task, token, amount);
    }

    public void markSuccess(CollectionTask task) {
        task.setStatus(CollectionTaskStatus.SUCCESS.getCode());
        task.setUpdatedAt(new Date());
        collectionTaskMapper.updateById(task);
        businessMetrics.incrementCollection();
        log.info("Collection task {} completed successfully", task.getId());
    }

    public void markFailed(CollectionTask task, String errorMessage) {
        task.setStatus(CollectionTaskStatus.FAILED.getCode());
        task.setErrorMessage(errorMessage);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setUpdatedAt(new Date());
        collectionTaskMapper.updateById(task);
        log.error("Collection task {} failed: {}", task.getId(), errorMessage);
    }
}
