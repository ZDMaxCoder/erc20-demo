package com.erc20.platform.blockchain.gas;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
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
public class StuckTransactionHandler {

    private final TransactionRecordMapper transactionRecordMapper;
    private final GasPriceCache gasPriceCache;
    private final GasProperties gasProperties;
    private final Web3j web3j;
    private final AlertService alertService;

    public StuckTransactionHandler(TransactionRecordMapper transactionRecordMapper,
                                   GasPriceCache gasPriceCache,
                                   GasProperties gasProperties,
                                   Web3j web3j,
                                   AlertService alertService) {
        this.transactionRecordMapper = transactionRecordMapper;
        this.gasPriceCache = gasPriceCache;
        this.gasProperties = gasProperties;
        this.web3j = web3j;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelay = 60000)
    public void scanStuckTransactions() {
        try {
            Date cutoff = new Date(System.currentTimeMillis()
                    - gasProperties.getStuckTimeoutMinutes() * 60 * 1000L);

            List<TransactionRecord> stuckTxs = transactionRecordMapper.selectList(
                    new LambdaQueryWrapper<TransactionRecord>()
                            .eq(TransactionRecord::getStatus, TxStatus.SUBMITTED.getCode())
                            .lt(TransactionRecord::getUpdatedAt, cutoff)
            );

            for (TransactionRecord tx : stuckTxs) {
                handleStuckTransaction(tx);
            }
        } catch (Exception e) {
            log.error("Failed to scan stuck transactions", e);
        }
    }

    private void handleStuckTransaction(TransactionRecord tx) {
        try {
            EthGetTransactionReceipt receiptResponse = web3j
                    .ethGetTransactionReceipt(tx.getTxHash()).send();
            Optional<TransactionReceipt> receipt = receiptResponse.getTransactionReceipt();

            if (receipt.isPresent()) {
                log.info("Transaction {} already confirmed on chain, updating status", tx.getTxHash());
                tx.setStatus(TxStatus.CONFIRMED.getCode());
                tx.setUpdatedAt(new Date());
                transactionRecordMapper.updateById(tx);
                return;
            }

            GasPrice originalPrice = GasPrice.builder()
                    .eip1559(false)
                    .gasPrice(tx.getGasPrice() != null ? BigInteger.valueOf(tx.getGasPrice()) : BigInteger.ZERO)
                    .build();
            GasPrice replacementPrice = gasPriceCache.getReplacementGasPrice(originalPrice);

            log.warn("Stuck transaction detected: txHash={}, nonce={}, age={}min, "
                            + "originalGasPrice={}, suggestedReplacementPrice={}",
                    tx.getTxHash(),
                    tx.getNonce(),
                    gasProperties.getStuckTimeoutMinutes(),
                    tx.getGasPrice(),
                    replacementPrice.getGasPrice());

            alertService.alert("STUCK_TRANSACTION", AlertLevel.WARN,
                    "Stuck tx: txHash=" + tx.getTxHash() + ", nonce=" + tx.getNonce());

        } catch (Exception e) {
            log.error("Failed to handle stuck transaction: txHash={}", tx.getTxHash(), e);
        }
    }
}
