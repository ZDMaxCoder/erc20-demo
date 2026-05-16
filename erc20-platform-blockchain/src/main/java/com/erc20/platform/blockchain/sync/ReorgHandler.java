package com.erc20.platform.blockchain.sync;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.BlockRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.BlockRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.service.AlertService;
import com.erc20.platform.service.DepositService;
import com.erc20.platform.service.WithdrawService;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class ReorgHandler {

    private final BlockRecordMapper blockRecordMapper;
    private final BlockSyncProgressMapper blockSyncProgressMapper;
    private final DepositRecordMapper depositRecordMapper;
    private final AlertService alertService;
    private final Web3jProvider web3jProvider;
    private final BlockSyncProperties properties;
    private final BusinessMetrics businessMetrics;
    private final DepositService depositService;
    private final WithdrawService withdrawService;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final TransactionRecordMapper transactionRecordMapper;

    public ReorgHandler(BlockRecordMapper blockRecordMapper,
                        BlockSyncProgressMapper blockSyncProgressMapper,
                        DepositRecordMapper depositRecordMapper,
                        AlertService alertService,
                        Web3jProvider web3jProvider,
                        BlockSyncProperties properties,
                        BusinessMetrics businessMetrics,
                        DepositService depositService,
                        WithdrawService withdrawService,
                        WithdrawRecordMapper withdrawRecordMapper,
                        TransactionRecordMapper transactionRecordMapper) {
        this.blockRecordMapper = blockRecordMapper;
        this.blockSyncProgressMapper = blockSyncProgressMapper;
        this.depositRecordMapper = depositRecordMapper;
        this.alertService = alertService;
        this.web3jProvider = web3jProvider;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
        this.depositService = depositService;
        this.withdrawService = withdrawService;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.transactionRecordMapper = transactionRecordMapper;
    }

    public long handleReorg(long currentBlockNumber, String expectedParentHash, String actualParentHash) throws IOException {
        log.warn("Reorg detected at block {}. Expected parentHash={}, actual={}",
                currentBlockNumber, expectedParentHash, actualParentHash);

        int chainId = properties.getChainId();
        int maxDepth = properties.getMaxReorgDepth();

        long checkBlock = currentBlockNumber - 1;
        long forkPoint = -1;
        int depth = 0;

        while (depth < maxDepth) {
            BlockRecord storedBlock = blockRecordMapper.selectOne(
                    new QueryWrapper<BlockRecord>()
                            .eq("chain_id", chainId)
                            .eq("block_number", checkBlock));

            if (storedBlock == null) {
                break;
            }

            final long blockToCheck = checkBlock;
            EthBlock chainBlock = web3jProvider.sendWithFailover(
                    web3j -> web3j.ethGetBlockByNumber(
                            new DefaultBlockParameterNumber(blockToCheck), false).send());

            String chainHash = chainBlock.getBlock().getHash();

            if (storedBlock.getBlockHash().equals(chainHash)) {
                forkPoint = checkBlock;
                break;
            }

            checkBlock--;
            depth++;
        }

        BlockSyncProgress progress = blockSyncProgressMapper.selectOne(
                new QueryWrapper<BlockSyncProgress>()
                        .eq("chain_id", chainId));

        if (forkPoint < 0) {
            log.error("Reorg exceeds max reorg depth of {} blocks", maxDepth);
            progress.setStatus("ERROR");
            progress.setErrorMessage("Reorg exceeds max reorg depth of " + maxDepth + " blocks");
            progress.setUpdatedAt(new Date());
            blockSyncProgressMapper.updateById(progress);

            alertService.alert("REORG", AlertLevel.CRITICAL,
                    "Reorg detected at block " + currentBlockNumber + " exceeding max depth " + maxDepth);

            throw new RuntimeException("Chain reorg exceeds max reorg depth of " + maxDepth + " blocks");
        }

        blockRecordMapper.update(null,
                new UpdateWrapper<BlockRecord>()
                        .eq("chain_id", chainId)
                        .gt("block_number", forkPoint)
                        .set("reorged", 1));

        List<Long> affectedBlockNumbers = collectAffectedBlockNumbers(forkPoint, currentBlockNumber);
        depositService.handleReorg(affectedBlockNumbers);

        revertConfirmedWithdrawals(forkPoint);
        resetConfirmedTransactionRecords(forkPoint);

        BlockRecord forkBlock = blockRecordMapper.selectOne(
                new QueryWrapper<BlockRecord>()
                        .eq("chain_id", chainId)
                        .eq("block_number", forkPoint));

        progress.setLastSyncedBlock(forkPoint);
        progress.setLastSyncedBlockHash(forkBlock != null ? forkBlock.getBlockHash() : null);
        progress.setStatus("RUNNING");
        progress.setUpdatedAt(new Date());
        blockSyncProgressMapper.updateById(progress);

        alertService.alert("REORG", AlertLevel.WARN,
                "Reorg detected at block " + currentBlockNumber + ", fork point at " + forkPoint);

        businessMetrics.incrementReorg();
        log.info("Reorg handled. Fork point: {}, rolled back {} blocks", forkPoint, currentBlockNumber - 1 - forkPoint);
        return forkPoint;
    }

    private List<Long> collectAffectedBlockNumbers(long forkPoint, long currentBlockNumber) {
        List<Long> blockNumbers = new ArrayList<>();
        for (long b = forkPoint + 1; b < currentBlockNumber; b++) {
            blockNumbers.add(b);
        }
        return blockNumbers;
    }

    private void revertConfirmedWithdrawals(long forkPoint) {
        List<TransactionRecord> confirmedTxs = transactionRecordMapper.selectList(
                new QueryWrapper<TransactionRecord>()
                        .eq("status", TxStatus.CONFIRMED.getCode())
                        .gt("block_number", forkPoint)
                        .eq("biz_type", "WITHDRAW"));

        for (TransactionRecord tx : confirmedTxs) {
            if (tx.getBizId() != null) {
                withdrawService.revertConfirmedWithdraw(tx.getBizId());
                log.warn("Reverted confirmed withdrawal {} (txHash={}) due to reorg", tx.getBizId(), tx.getTxHash());
            }
        }
    }

    private void resetConfirmedTransactionRecords(long forkPoint) {
        transactionRecordMapper.update(null,
                new UpdateWrapper<TransactionRecord>()
                        .eq("status", TxStatus.CONFIRMED.getCode())
                        .gt("block_number", forkPoint)
                        .set("status", TxStatus.PENDING.getCode())
                        .set("block_number", null)
                        .set("block_hash", null));
    }
}
