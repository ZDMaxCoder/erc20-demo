package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.domain.entity.DepositRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class DepositConfirmJob {

    private static final int DEFAULT_CHAIN_ID = 1;

    private final DepositRecordMapper depositRecordMapper;
    private final BlockSyncProgressMapper blockSyncProgressMapper;
    private final DepositService depositService;

    public DepositConfirmJob(DepositRecordMapper depositRecordMapper,
                             BlockSyncProgressMapper blockSyncProgressMapper,
                             DepositService depositService) {
        this.depositRecordMapper = depositRecordMapper;
        this.blockSyncProgressMapper = blockSyncProgressMapper;
        this.depositService = depositService;
    }

    @Scheduled(fixedDelay = 10000)
    public void confirmDeposits() {
        List<DepositRecord> confirming = depositRecordMapper.selectList(
                new LambdaQueryWrapper<DepositRecord>()
                        .eq(DepositRecord::getStatus, DepositStatus.CONFIRMING.getCode()));

        if (confirming.isEmpty()) {
            return;
        }

        BlockSyncProgress progress = blockSyncProgressMapper.selectOne(
                new LambdaQueryWrapper<BlockSyncProgress>()
                        .eq(BlockSyncProgress::getChainId, DEFAULT_CHAIN_ID));
        if (progress == null) {
            log.warn("No sync progress found for chain {}", DEFAULT_CHAIN_ID);
            return;
        }

        long currentBlock = progress.getLastSyncedBlock();

        for (DepositRecord deposit : confirming) {
            int confirmations = (int) (currentBlock - deposit.getBlockNumber());
            deposit.setConfirmations(Math.max(confirmations, 0));
            deposit.setUpdatedAt(new Date());
            depositRecordMapper.updateById(deposit);

            if (confirmations >= deposit.getRequiredConfirmations()) {
                depositService.creditDeposit(deposit.getId());
                log.info("Deposit {} confirmed with {} confirmations", deposit.getId(), confirmations);
            }
        }
    }
}
