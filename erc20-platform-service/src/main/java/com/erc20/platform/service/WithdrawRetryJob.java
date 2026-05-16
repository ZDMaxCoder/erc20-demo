package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.gateway.WithdrawTransactionSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class WithdrawRetryJob {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long BROADCASTING_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final long APPROVED_TIMEOUT_MS = 5 * 60 * 1000L;
    private static final long SIGNING_TIMEOUT_MS = 2 * 60 * 1000L;

    private final WithdrawRecordMapper withdrawRecordMapper;
    private final WithdrawService withdrawService;
    private final WithdrawTransactionSender transactionSender;
    private final AlertService alertService;

    public WithdrawRetryJob(WithdrawRecordMapper withdrawRecordMapper,
                            WithdrawService withdrawService,
                            WithdrawTransactionSender transactionSender,
                            AlertService alertService) {
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.withdrawService = withdrawService;
        this.transactionSender = transactionSender;
        this.alertService = alertService;
    }

    @Scheduled(fixedDelay = 30000)
    public void scanStuckWithdrawals() {
        scanStuckSigning();
        scanStuckBroadcasting();
        scanStuckApproved();
    }

    private void scanStuckSigning() {
        Date cutoff = new Date(System.currentTimeMillis() - SIGNING_TIMEOUT_MS);
        List<WithdrawRecord> stuck = withdrawRecordMapper.selectList(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getStatus, WithdrawStatus.SIGNING.getCode())
                        .le(WithdrawRecord::getUpdatedAt, cutoff));

        for (WithdrawRecord record : stuck) {
            try {
                log.info("Withdrawal {} stuck in SIGNING, resetting to APPROVED", record.getId());
                record.setStatus(WithdrawStatus.APPROVED.getCode());
                record.setUpdatedAt(new Date());
                withdrawRecordMapper.updateById(record);
            } catch (Exception e) {
                log.error("Failed to handle stuck signing withdrawal {}", record.getId(), e);
            }
        }
    }

    private void scanStuckBroadcasting() {
        Date cutoff = new Date(System.currentTimeMillis() - BROADCASTING_TIMEOUT_MS);
        List<WithdrawRecord> stuck = withdrawRecordMapper.selectList(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getStatus, WithdrawStatus.BROADCASTING.getCode())
                        .le(WithdrawRecord::getUpdatedAt, cutoff));

        for (WithdrawRecord record : stuck) {
            try {
                handleStuckBroadcasting(record);
            } catch (Exception e) {
                log.error("Failed to handle stuck broadcasting withdrawal {}", record.getId(), e);
            }
        }
    }

    private void handleStuckBroadcasting(WithdrawRecord record) {
        if (record.getRetryCount() >= MAX_RETRY_COUNT) {
            log.warn("Withdrawal {} exceeded max retry count, marking FAILED", record.getId());
            withdrawService.failWithdraw(record.getId(), "Max retry count exceeded");
            return;
        }

        TxStatus chainStatus = transactionSender.queryTransactionStatus(record.getTxHash());

        if (chainStatus == TxStatus.CONFIRMED) {
            log.info("Withdrawal {} tx confirmed on-chain, confirming", record.getId());
            withdrawService.confirmWithdraw(record.getId(), record.getTxHash(), 0L);
        } else if (chainStatus == TxStatus.FAILED) {
            log.info("Withdrawal {} tx failed on-chain, marking failed", record.getId());
            withdrawService.failWithdraw(record.getId(), "Transaction failed on-chain");
        } else {
            log.info("Withdrawal {} tx still pending, resetting to APPROVED for re-execution", record.getId());
            alertService.alert("STUCK_WITHDRAW", AlertLevel.WARN,
                    String.format("Withdrawal %d stuck in BROADCASTING with pending tx %s", record.getId(), record.getTxHash()),
                    String.valueOf(record.getId()));
            record.setStatus(WithdrawStatus.APPROVED.getCode());
            record.setRetryCount(record.getRetryCount() + 1);
            record.setTxHash(null);
            record.setUpdatedAt(new Date());
            withdrawRecordMapper.updateById(record);
        }
    }

    private void scanStuckApproved() {
        Date cutoff = new Date(System.currentTimeMillis() - APPROVED_TIMEOUT_MS);
        List<WithdrawRecord> stuck = withdrawRecordMapper.selectList(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getStatus, WithdrawStatus.APPROVED.getCode())
                        .le(WithdrawRecord::getUpdatedAt, cutoff));

        for (WithdrawRecord record : stuck) {
            try {
                log.info("Re-executing stuck approved withdrawal {}", record.getId());
                withdrawService.executeWithdraw(record.getId());
            } catch (Exception e) {
                log.error("Failed to re-execute approved withdrawal {}", record.getId(), e);
            }
        }
    }
}
