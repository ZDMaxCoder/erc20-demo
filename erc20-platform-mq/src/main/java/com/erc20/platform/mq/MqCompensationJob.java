package com.erc20.platform.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.DepositService;
import com.erc20.platform.service.dto.WithdrawExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

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

    public MqCompensationJob(DepositRecordMapper depositRecordMapper,
                             WithdrawRecordMapper withdrawRecordMapper,
                             DepositService depositService,
                             MqProducer mqProducer) {
        this.depositRecordMapper = depositRecordMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.depositService = depositService;
        this.mqProducer = mqProducer;
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

        for (DepositRecord record : stuck) {
            try {
                log.info("Compensating stuck deposit {}: CONFIRMING for >30min", record.getId());
                depositService.creditDeposit(record.getId());
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
