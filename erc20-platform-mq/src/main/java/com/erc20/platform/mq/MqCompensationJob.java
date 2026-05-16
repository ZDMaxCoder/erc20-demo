package com.erc20.platform.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.AlertService;
import com.erc20.platform.service.DepositService;
import com.erc20.platform.service.dto.WithdrawExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class MqCompensationJob {

    private static final long DEPOSIT_CONFIRMING_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long WITHDRAW_APPROVED_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final int MAX_COMPENSATION_COUNT = 5;

    private final DepositRecordMapper depositRecordMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final DepositService depositService;
    private final MqProducer mqProducer;
    private final Web3j web3j;
    private final TokenConfigMapper tokenConfigMapper;
    private final AlertService alertService;

    public MqCompensationJob(DepositRecordMapper depositRecordMapper,
                             WithdrawRecordMapper withdrawRecordMapper,
                             DepositService depositService,
                             MqProducer mqProducer,
                             Web3j web3j,
                             TokenConfigMapper tokenConfigMapper,
                             AlertService alertService) {
        this.depositRecordMapper = depositRecordMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.depositService = depositService;
        this.mqProducer = mqProducer;
        this.web3j = web3j;
        this.tokenConfigMapper = tokenConfigMapper;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelay = 300000)
    public void compensate() {
        compensateStuckDeposits();
        compensateStuckWithdrawals();
    }

    private void compensateStuckDeposits() {
        Date cutoff = new Date(System.currentTimeMillis() - DEPOSIT_CONFIRMING_TIMEOUT_MS);
        List<DepositRecord> stuck = depositRecordMapper.selectList(
                new LambdaQueryWrapper<DepositRecord>()
                        .eq(DepositRecord::getStatus, DepositStatus.CONFIRMING.getCode())
                        .le(DepositRecord::getUpdatedAt, cutoff));

        if (stuck.isEmpty()) {
            return;
        }

        BigInteger currentBlock;
        try {
            EthBlockNumber ethBlockNumber = web3j.ethBlockNumber().send();
            currentBlock = ethBlockNumber.getBlockNumber();
        } catch (Exception e) {
            log.error("Failed to query current block number, skipping deposit compensation", e);
            return;
        }

        for (DepositRecord record : stuck) {
            try {
                log.info("Compensating stuck deposit {}: CONFIRMING for >30min", record.getId());

                EthGetTransactionReceipt receiptResponse = web3j
                        .ethGetTransactionReceipt(record.getTxHash()).send();
                Optional<TransactionReceipt> receiptOpt = receiptResponse.getTransactionReceipt();

                if (!receiptOpt.isPresent()) {
                    log.error("No receipt found on chain for deposit {}, txHash={}",
                            record.getId(), record.getTxHash());
                    alertService.alert("DEPOSIT_TX_MISSING", AlertLevel.CRITICAL,
                            "No receipt on chain for stuck deposit: id=" + record.getId()
                                    + ", txHash=" + record.getTxHash(),
                            String.valueOf(record.getId()));
                    continue;
                }

                TransactionReceipt receipt = receiptOpt.get();
                BigInteger receiptBlock = receipt.getBlockNumber();
                long confirmations = currentBlock.subtract(receiptBlock).longValue();

                TokenConfig tokenConfig = tokenConfigMapper.selectById(record.getTokenId());
                int requiredConfirmations = tokenConfig.getDepositConfirmBlocks();

                if (confirmations >= requiredConfirmations) {
                    depositService.creditDeposit(record.getId());
                } else {
                    log.warn("Deposit {} has only {} confirmations (required {}), not crediting",
                            record.getId(), confirmations, requiredConfirmations);
                    record.setUpdatedAt(new Date());
                    depositRecordMapper.updateById(record);
                    alertService.alert("DEPOSIT_STUCK", AlertLevel.WARN,
                            "Stuck deposit with insufficient confirmations: id=" + record.getId()
                                    + ", txHash=" + record.getTxHash()
                                    + ", confirmations=" + confirmations
                                    + ", required=" + requiredConfirmations,
                            String.valueOf(record.getId()));
                }
            } catch (Exception e) {
                log.error("Failed to compensate deposit {}", record.getId(), e);
            }
        }
    }

    private void compensateStuckWithdrawals() {
        Date cutoff = new Date(System.currentTimeMillis() - WITHDRAW_APPROVED_TIMEOUT_MS);
        List<WithdrawRecord> stuck = withdrawRecordMapper.selectList(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getStatus, WithdrawStatus.APPROVED.getCode())
                        .le(WithdrawRecord::getUpdatedAt, cutoff));

        for (WithdrawRecord record : stuck) {
            if (record.getRetryCount() >= MAX_COMPENSATION_COUNT) {
                log.warn("Withdrawal {} reached max compensation count {}, skipping",
                        record.getId(), MAX_COMPENSATION_COUNT);
                continue;
            }

            try {
                log.info("Compensating stuck withdrawal {}: APPROVED for >5min, retryCount={}",
                        record.getId(), record.getRetryCount());

                WithdrawExecuteMessage payload = WithdrawExecuteMessage.builder()
                        .withdrawId(record.getId())
                        .build();
                mqProducer.send(MqConstants.TOPIC_WITHDRAW_EXECUTE, MqConstants.TAG_APPROVED,
                        String.valueOf(record.getId()), payload);

                record.setRetryCount(record.getRetryCount() + 1);
                record.setUpdatedAt(new Date());
                withdrawRecordMapper.updateById(record);
            } catch (Exception e) {
                log.error("Failed to compensate withdrawal {}", record.getId(), e);
            }
        }
    }
}
