package com.erc20.platform.blockchain.sync;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.dal.mapper.BlockRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.domain.entity.BlockRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.service.AlertService;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.io.IOException;
import java.util.Date;

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

    public ReorgHandler(BlockRecordMapper blockRecordMapper,
                        BlockSyncProgressMapper blockSyncProgressMapper,
                        DepositRecordMapper depositRecordMapper,
                        AlertService alertService,
                        Web3jProvider web3jProvider,
                        BlockSyncProperties properties,
                        BusinessMetrics businessMetrics) {
        this.blockRecordMapper = blockRecordMapper;
        this.blockSyncProgressMapper = blockSyncProgressMapper;
        this.depositRecordMapper = depositRecordMapper;
        this.alertService = alertService;
        this.web3jProvider = web3jProvider;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
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

        depositRecordMapper.update(null,
                new UpdateWrapper<DepositRecord>()
                        .gt("block_number", forkPoint)
                        .ne("status", DepositStatus.REORGED.getCode())
                        .set("status", DepositStatus.REORGED.getCode()));

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
}
