package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.blockchain.adapter.model.TransferOutcome;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import com.erc20.platform.blockchain.erc20.ERC20TransferEventParser;
import com.erc20.platform.blockchain.erc20.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TransferConfirmer {

    private final Web3j web3j;
    private final ERC20TransferEventParser eventParser;
    private final TokenRiskProfileRegistry tokenRiskProfileRegistry;
    private final BalanceDiffChecker balanceDiffChecker;

    public TransferConfirmer(Web3j web3j, ERC20TransferEventParser eventParser,
                             TokenRiskProfileRegistry tokenRiskProfileRegistry,
                             BalanceDiffChecker balanceDiffChecker) {
        this.web3j = web3j;
        this.eventParser = eventParser;
        this.tokenRiskProfileRegistry = tokenRiskProfileRegistry;
        this.balanceDiffChecker = balanceDiffChecker;
    }

    public TransferResult confirm(String txHash, String contract,
                                  BigInteger expectedAmount, String toAddress) {
        return doConfirm(txHash, contract, expectedAmount, toAddress, 0, null);
    }

    public TransferResult confirm(String txHash, String contract,
                                  BigInteger expectedAmount, String toAddress,
                                  BigInteger balanceBefore) {
        return doConfirm(txHash, contract, expectedAmount, toAddress, 0, balanceBefore);
    }

    public TransferResult confirm(String txHash, String contract,
                                  BigInteger expectedAmount, String toAddress,
                                  int minConfirmations) {
        return doConfirm(txHash, contract, expectedAmount, toAddress, minConfirmations, null);
    }

    private TransferResult doConfirm(String txHash, String contract,
                                     BigInteger expectedAmount, String toAddress,
                                     int minConfirmations, BigInteger balanceBefore) {
        Optional<TransactionReceipt> receiptOpt;
        try {
            receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
        } catch (IOException e) {
            log.error("Failed to fetch receipt for tx {}", txHash, e);
            throw new RuntimeException("Failed to fetch transaction receipt: " + txHash, e);
        }

        if (!receiptOpt.isPresent()) {
            return TransferResult.pending(txHash);
        }

        TransactionReceipt receipt = receiptOpt.get();

        if (!"0x1".equals(receipt.getStatus())) {
            return TransferResult.builder()
                    .outcome(TransferOutcome.FAILED)
                    .txHash(txHash)
                    .blockNumber(receipt.getBlockNumber().longValue())
                    .anomalyReason("receipt status failed")
                    .build();
        }

        List<TransferEvent> events = eventParser.parseFromReceipt(receipt, contract);

        if (events.isEmpty()) {
            return TransferResult.builder()
                    .outcome(TransferOutcome.FAILED)
                    .txHash(txHash)
                    .blockNumber(receipt.getBlockNumber().longValue())
                    .anomalyReason("Transfer event not found")
                    .build();
        }

        if (minConfirmations > 0) {
            BigInteger currentBlock;
            try {
                currentBlock = web3j.ethBlockNumber().send().getBlockNumber();
            } catch (IOException e) {
                log.error("Failed to fetch current block number for tx {}", txHash, e);
                throw new RuntimeException("Failed to fetch current block number", e);
            }
            BigInteger confirmations = currentBlock.subtract(receipt.getBlockNumber());
            if (confirmations.compareTo(BigInteger.valueOf(minConfirmations)) < 0) {
                return TransferResult.pending(txHash);
            }
        }

        BigInteger actualAmount = BigInteger.ZERO;
        for (TransferEvent event : events) {
            actualAmount = actualAmount.add(event.getValue());
        }

        TransferOutcome outcome;
        if (expectedAmount == null) {
            outcome = TransferOutcome.SUCCESS;
        } else {
            outcome = actualAmount.compareTo(expectedAmount) == 0
                    ? TransferOutcome.SUCCESS
                    : TransferOutcome.ANOMALY;
        }

        TransferResult.Builder builder = TransferResult.builder()
                .outcome(outcome)
                .txHash(txHash)
                .blockNumber(receipt.getBlockNumber().longValue())
                .actualAmount(actualAmount)
                .expectedAmount(expectedAmount)
                .events(events);

        if (outcome == TransferOutcome.ANOMALY) {
            builder.anomalyReason("Amount mismatch: expected=" + expectedAmount + ", actual=" + actualAmount);
        }

        if (outcome == TransferOutcome.SUCCESS && balanceBefore != null) {
            TokenRiskProfile profile = tokenRiskProfileRegistry.getProfile(contract);
            if (profile != null && profile.requiresBalanceDiff()) {
                BigInteger balanceAfter = balanceDiffChecker.queryBalance(contract, toAddress);
                BigInteger diff = balanceDiffChecker.computeDiff(balanceBefore, balanceAfter);
                if (diff != null && expectedAmount != null) {
                    builder.balanceDiff(diff);
                    int cmp = diff.compareTo(expectedAmount);
                    if (cmp != 0) {
                        builder.outcome(TransferOutcome.ANOMALY);
                        builder.anomalyReason("Balance diff mismatch: expected=" + expectedAmount + ", balanceDiff=" + diff);
                        if (diff.compareTo(expectedAmount) < 0) {
                            builder.actualAmount(diff);
                        }
                    }
                }
            }
        }

        return builder.build();
    }
}
