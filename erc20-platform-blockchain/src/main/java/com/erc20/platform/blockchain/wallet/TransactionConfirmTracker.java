package com.erc20.platform.blockchain.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.erc20.ERC20TransferEventParser;
import com.erc20.platform.blockchain.erc20.TransferEvent;
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
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
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
    private final ERC20TransferEventParser transferEventParser;
    private final TokenConfigMapper tokenConfigMapper;
    private final int chainId;

    public TransactionConfirmTracker(TransactionRecordMapper transactionRecordMapper,
                                     NonceManager nonceManager,
                                     Web3j web3j,
                                     RocketMQTemplate rocketMQTemplate,
                                     ERC20TransferEventParser transferEventParser,
                                     TokenConfigMapper tokenConfigMapper,
                                     @Value("${blockchain.sync.chain-id:1}") int chainId) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.nonceManager = nonceManager;
        this.web3j = web3j;
        this.rocketMQTemplate = rocketMQTemplate;
        this.transferEventParser = transferEventParser;
        this.tokenConfigMapper = tokenConfigMapper;
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

            tx.setBlockNumber(receipt.getBlockNumber().longValue());
            tx.setBlockHash(receipt.getBlockHash());
            tx.setGasUsed(receipt.getGasUsed().longValue());
            tx.setUpdatedAt(new Date());

            if ("0x1".equals(receipt.getStatus())) {
                BigInteger actualAmount = verifyTransferEvent(tx, receipt);
                if (actualAmount != null) {
                    tx.setStatus(TxStatus.CONFIRMED.getCode());
                    transactionRecordMapper.updateById(tx);
                    nonceManager.confirmNonce(chainId, tx.getFromAddress(), tx.getNonce());
                    publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.CONFIRMED.getCode(),
                            tx.getBlockNumber(), tx.getBlockHash(), actualAmount);
                } else {
                    tx.setStatus(TxStatus.FAILED.getCode());
                    tx.setErrorMessage("Transfer event not found in receipt");
                    transactionRecordMapper.updateById(tx);
                    publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.FAILED.getCode(),
                            tx.getBlockNumber(), tx.getBlockHash(), null);
                }
            } else {
                tx.setStatus(TxStatus.FAILED.getCode());
                transactionRecordMapper.updateById(tx);
                publishStatusChange(tx.getTxHash(), previousStatus, TxStatus.FAILED.getCode(),
                        tx.getBlockNumber(), tx.getBlockHash(), null);
            }
        } catch (Exception e) {
            log.error("Failed to check confirmation for tx {}", tx.getTxHash(), e);
        }
    }

    private BigInteger verifyTransferEvent(TransactionRecord tx, TransactionReceipt receipt) {
        if (tx.getTokenId() == null) {
            return null;
        }
        TokenConfig tokenConfig = tokenConfigMapper.selectById(tx.getTokenId());
        if (tokenConfig == null || tokenConfig.getContractAddress() == null) {
            return null;
        }
        List<TransferEvent> events = transferEventParser.parseFromReceipt(receipt, tokenConfig.getContractAddress());
        if (events.isEmpty()) {
            return null;
        }
        BigInteger totalAmount = BigInteger.ZERO;
        for (TransferEvent event : events) {
            totalAmount = totalAmount.add(event.getValue());
        }
        return totalAmount;
    }

    private void publishStatusChange(String txHash, String fromStatus, String toStatus,
                                     Long blockNumber, String blockHash, BigInteger actualAmount) {
        try {
            TxStatusChangedMessage message = TxStatusChangedMessage.builder()
                    .txHash(txHash)
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .blockNumber(blockNumber)
                    .blockHash(blockHash)
                    .actualAmount(actualAmount)
                    .build();
            rocketMQTemplate.convertAndSend(TX_STATUS_TOPIC, message);
        } catch (Exception e) {
            log.error("Failed to publish tx status change for {}", txHash, e);
        }
    }
}
