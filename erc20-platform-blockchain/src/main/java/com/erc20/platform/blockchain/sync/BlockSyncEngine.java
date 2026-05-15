package com.erc20.platform.blockchain.sync;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.blockchain.erc20.TransferEvent;
import com.erc20.platform.common.lock.DistributedLock;
import com.erc20.platform.dal.mapper.BlockRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.domain.entity.BlockRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class BlockSyncEngine {

    private final Web3jProvider web3jProvider;
    private final BlockRecordMapper blockRecordMapper;
    private final BlockSyncProgressMapper blockSyncProgressMapper;
    private final ReorgHandler reorgHandler;
    private final TransferEventExtractor transferEventExtractor;
    private final BlockEventPublisher blockEventPublisher;
    private final BlockSyncProperties properties;
    private final BusinessMetrics businessMetrics;

    public BlockSyncEngine(Web3jProvider web3jProvider,
                           BlockRecordMapper blockRecordMapper,
                           BlockSyncProgressMapper blockSyncProgressMapper,
                           ReorgHandler reorgHandler,
                           TransferEventExtractor transferEventExtractor,
                           BlockEventPublisher blockEventPublisher,
                           BlockSyncProperties properties,
                           BusinessMetrics businessMetrics) {
        this.web3jProvider = web3jProvider;
        this.blockRecordMapper = blockRecordMapper;
        this.blockSyncProgressMapper = blockSyncProgressMapper;
        this.reorgHandler = reorgHandler;
        this.transferEventExtractor = transferEventExtractor;
        this.blockEventPublisher = blockEventPublisher;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
    }

    @Scheduled(fixedDelayString = "${blockchain.sync.poll-interval:3000}")
    @DistributedLock(key = "'block_sync:' + #root.target.properties.chainId", leaseTime = 30)
    public void syncNextBatch() {
        try {
            int chainId = properties.getChainId();
            int batchSize = properties.getBatchSize();

            BlockSyncProgress progress = getOrCreateProgress(chainId);
            if (!"RUNNING".equals(progress.getStatus())) {
                return;
            }

            for (int i = 0; i < batchSize; i++) {
                long nextBlock = progress.getLastSyncedBlock() + 1;
                boolean synced = syncOneBlock(nextBlock, progress);
                if (!synced) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Block sync error: {}", e.getMessage(), e);
        }
    }

    private boolean syncOneBlock(long blockNumber, BlockSyncProgress progress) throws Exception {
        EthBlock ethBlock = web3jProvider.sendWithFailover(
                web3j -> web3j.ethGetBlockByNumber(
                        new DefaultBlockParameterNumber(blockNumber), false).send());

        if (ethBlock.getBlock() == null) {
            return false;
        }

        EthBlock.Block block = ethBlock.getBlock();
        String parentHash = block.getParentHash();
        String storedParentHash = progress.getLastSyncedBlockHash();

        if (storedParentHash != null && !storedParentHash.equals(parentHash)) {
            reorgHandler.handleReorg(blockNumber, storedParentHash, parentHash);
            return false;
        }

        processBlock(block, progress);
        return true;
    }

    private void processBlock(EthBlock.Block block, BlockSyncProgress progress) throws Exception {
        long blockNumber = block.getNumber().longValue();
        String blockHash = block.getHash();

        BlockRecord record = BlockRecord.builder()
                .chainId(properties.getChainId())
                .blockNumber(blockNumber)
                .blockHash(blockHash)
                .parentHash(block.getParentHash())
                .blockTimestamp(new Date(block.getTimestamp().longValue() * 1000))
                .txCount(block.getTransactions() != null ? block.getTransactions().size() : 0)
                .synced(1)
                .reorged(0)
                .createdAt(new Date())
                .build();

        blockRecordMapper.insert(record);

        List<TransferEvent> events = transferEventExtractor.extractFromBlock(blockNumber, block);
        if (!events.isEmpty()) {
            blockEventPublisher.publish(events);
        }

        progress.setLastSyncedBlock(blockNumber);
        progress.setLastSyncedBlockHash(blockHash);
        progress.setUpdatedAt(new Date());
        blockSyncProgressMapper.updateById(progress);

        businessMetrics.incrementBlockSynced();
        log.debug("Synced block {} ({})", blockNumber, blockHash);
    }

    private BlockSyncProgress getOrCreateProgress(int chainId) {
        BlockSyncProgress progress = blockSyncProgressMapper.selectOne(
                new QueryWrapper<BlockSyncProgress>().eq("chain_id", chainId));

        if (progress == null) {
            progress = BlockSyncProgress.builder()
                    .chainId(chainId)
                    .lastSyncedBlock(resolveStartBlock())
                    .status("RUNNING")
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            blockSyncProgressMapper.insert(progress);
        }
        return progress;
    }

    private long resolveStartBlock() {
        String startBlock = properties.getStartBlock();
        if ("latest".equalsIgnoreCase(startBlock)) {
            try {
                return web3jProvider.sendWithFailover(
                        web3j -> web3j.ethBlockNumber().send())
                        .getBlockNumber().longValue();
            } catch (Exception e) {
                log.error("Failed to get latest block number: {}", e.getMessage());
                return 0L;
            }
        }
        return Long.parseLong(startBlock);
    }
}
