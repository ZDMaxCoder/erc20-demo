package com.erc20.platform.blockchain.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TransactionRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TransactionConfirmTracker {

    private static final String TX_STATUS_TOPIC = "tx-status-changed";

    private final TransactionRecordMapper transactionRecordMapper;
    private final NonceManager nonceManager;
    private final Web3j web3j;
    private final RocketMQTemplate rocketMQTemplate;
    private final int chainId;

    public TransactionConfirmTracker(TransactionRecordMapper transactionRecordMapper,
                                     NonceManager nonceManager,
                                     Web3j web3j,
                                     RocketMQTemplate rocketMQTemplate,
                                     @Value("${blockchain.sync.chain-id:1}") int chainId) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.nonceManager = nonceManager;
        this.web3j = web3j;
        this.rocketMQTemplate = rocketMQTemplate;
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
            EthGetTransactionReceipt receiptResponse = web3j
                    .ethGetTransactionReceipt(tx.getTxHash()).send();
            Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

            if (!receiptOpt.isPresent()) {
                return;
            }

            TransactionReceipt receipt = receiptOpt.get();
            String previousStatus = tx.getStatus();

            if ("0x1".equals(receipt.getStatus())) {
                tx.setStatus(TxStatus.CONFIRMED.getCode());
                tx.setBlockNumber(receipt.getBlockNumber().longValue());
                tx.setBlockHash(receipt.getBlockHash());
                tx.setGasUsed(receipt.getGasUsed().longValue());
                tx.setUpdatedAt(new Date());
                transactionRecordMapper.updateById(tx);

                nonceManager.confirmNonce(chainId, tx.getFromAddress(), tx.getNonce());
                publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.CONFIRMED.getCode(),
                        tx.getBlockNumber(), tx.getBlockHash());
            } else {
                tx.setStatus(TxStatus.FAILED.getCode());
                tx.setBlockNumber(receipt.getBlockNumber().longValue());
                tx.setBlockHash(receipt.getBlockHash());
                tx.setGasUsed(receipt.getGasUsed().longValue());
                tx.setUpdatedAt(new Date());
                transactionRecordMapper.updateById(tx);

                publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.FAILED.getCode(),
                        tx.getBlockNumber(), tx.getBlockHash());
            }
        } catch (Exception e) {
            log.error("Failed to check confirmation for tx {}", tx.getTxHash(), e);
        }
    }

    private void publishStatusChange(String txHash, String fromStatus, String toStatus,
                                     Long blockNumber, String blockHash) {
        try {
            TxStatusChangedMessage message = TxStatusChangedMessage.builder()
                    .txHash(txHash)
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .blockNumber(blockNumber)
                    .blockHash(blockHash)
                    .build();
            rocketMQTemplate.convertAndSend(TX_STATUS_TOPIC, message);
        } catch (Exception e) {
            log.error("Failed to publish tx status change for {}", txHash, e);
        }
    }
}
