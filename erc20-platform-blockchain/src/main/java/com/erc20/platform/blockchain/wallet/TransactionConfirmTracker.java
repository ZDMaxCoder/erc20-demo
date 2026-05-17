package com.erc20.platform.blockchain.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.adapter.ConsecutiveFailureBreaker;
import com.erc20.platform.blockchain.adapter.TransferConfirmer;
import com.erc20.platform.blockchain.adapter.model.TransferOutcome;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.TransactionRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class TransactionConfirmTracker {

    private static final String TX_STATUS_TOPIC = "tx-status-changed";

    private final TransactionRecordMapper transactionRecordMapper;
    private final NonceManager nonceManager;
    private final TransferConfirmer transferConfirmer;
    private final RocketMQTemplate rocketMQTemplate;
    private final TokenConfigMapper tokenConfigMapper;
    private final ConsecutiveFailureBreaker consecutiveFailureBreaker;
    private final int chainId;

    public TransactionConfirmTracker(TransactionRecordMapper transactionRecordMapper,
                                     NonceManager nonceManager,
                                     TransferConfirmer transferConfirmer,
                                     RocketMQTemplate rocketMQTemplate,
                                     TokenConfigMapper tokenConfigMapper,
                                     ConsecutiveFailureBreaker consecutiveFailureBreaker,
                                     @Value("${blockchain.sync.chain-id:1}") int chainId) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.nonceManager = nonceManager;
        this.transferConfirmer = transferConfirmer;
        this.rocketMQTemplate = rocketMQTemplate;
        this.tokenConfigMapper = tokenConfigMapper;
        this.consecutiveFailureBreaker = consecutiveFailureBreaker;
        this.chainId = chainId;
    }

    @Scheduled(fixedDelay = 5000)
    public void scanPendingTransactions() {
        try {
            List<TransactionRecord> pendingTxs = transactionRecordMapper.selectList(
                    new LambdaQueryWrapper<TransactionRecord>()
                            .eq(TransactionRecord::getStatus, TxStatus.PENDING.getCode()));

            for (TransactionRecord tx : pendingTxs) {
                checkConfirmation(tx);
            }
        } catch (Exception e) {
            log.error("Failed to scan pending transactions", e);
        }
    }

    private void checkConfirmation(TransactionRecord tx) {
        try {
            TokenConfig tokenConfig = resolveTokenConfig(tx.getTokenId());
            if (tokenConfig == null || tokenConfig.getContractAddress() == null) {
                log.warn("No contract address for tx {} (tokenId={}), skipping", tx.getTxHash(), tx.getTokenId());
                return;
            }

            String contractAddress = tokenConfig.getContractAddress();
            int minConfirmations = tokenConfig.getDepositConfirmBlocks() != null
                    ? tokenConfig.getDepositConfirmBlocks() : 0;

            BigInteger expectedAmount = tx.getAmount() != null ? BigInteger.valueOf(tx.getAmount()) : null;
            TransferResult result = transferConfirmer.confirm(
                    tx.getTxHash(), contractAddress, expectedAmount, tx.getToAddress(), minConfirmations);

            TransferOutcome outcome = result.getOutcome();
            if (outcome == TransferOutcome.PENDING) {
                return;
            }

            String previousStatus = tx.getStatus();
            tx.setUpdatedAt(new Date());
            if (result.getBlockNumber() != null) {
                tx.setBlockNumber(result.getBlockNumber());
            }

            switch (outcome) {
                case SUCCESS:
                    tx.setStatus(TxStatus.CONFIRMED.getCode());
                    transactionRecordMapper.updateById(tx);
                    nonceManager.confirmNonce(chainId, tx.getFromAddress(), tx.getNonce());
                    consecutiveFailureBreaker.recordSuccess(contractAddress);
                    publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.CONFIRMED.getCode(),
                            tx.getBlockNumber(), result.getActualAmount(), false, null);
                    break;
                case ANOMALY:
                    tx.setStatus(TxStatus.CONFIRMED.getCode());
                    transactionRecordMapper.updateById(tx);
                    nonceManager.confirmNonce(chainId, tx.getFromAddress(), tx.getNonce());
                    publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.CONFIRMED.getCode(),
                            tx.getBlockNumber(), result.getActualAmount(), true, result.getAnomalyReason());
                    break;
                case FAILED:
                    tx.setStatus(TxStatus.FAILED.getCode());
                    tx.setErrorMessage(result.getAnomalyReason());
                    transactionRecordMapper.updateById(tx);
                    consecutiveFailureBreaker.recordFailure(contractAddress);
                    publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.FAILED.getCode(),
                            tx.getBlockNumber(), null, false, null);
                    break;
                default:
                    log.warn("Unexpected outcome {} for tx {}", outcome, tx.getTxHash());
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to check confirmation for tx {}", tx.getTxHash(), e);
        }
    }

    private TokenConfig resolveTokenConfig(Long tokenId) {
        if (tokenId == null) {
            return null;
        }
        return tokenConfigMapper.selectById(tokenId);
    }

    private void publishStatusChange(String txHash, String fromStatus, String toStatus,
                                     Long blockNumber, BigInteger actualAmount,
                                     boolean anomaly, String anomalyReason) {
        try {
            TxStatusChangedMessage message = TxStatusChangedMessage.builder()
                    .txHash(txHash)
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .blockNumber(blockNumber)
                    .actualAmount(actualAmount)
                    .anomaly(anomaly)
                    .anomalyReason(anomalyReason)
                    .build();
            rocketMQTemplate.convertAndSend(TX_STATUS_TOPIC, message);
        } catch (Exception e) {
            log.error("Failed to publish tx status change for {}", txHash, e);
        }
    }
}
