package com.erc20.platform.blockchain.health;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

@Slf4j
@Component
public class BlockSyncHealthIndicator extends AbstractHealthIndicator {

    private static final long MAX_ACCEPTABLE_DELAY = 30;

    private final BlockSyncProgressMapper blockSyncProgressMapper;
    private final Web3jProvider web3jProvider;
    private final AlertService alertService;

    public BlockSyncHealthIndicator(BlockSyncProgressMapper blockSyncProgressMapper,
                                    Web3jProvider web3jProvider,
                                    AlertService alertService) {
        this.blockSyncProgressMapper = blockSyncProgressMapper;
        this.web3jProvider = web3jProvider;
        this.alertService = alertService;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            BlockSyncProgress progress = blockSyncProgressMapper.selectOne(
                    new QueryWrapper<BlockSyncProgress>().last("LIMIT 1"));
            if (progress == null) {
                builder.unknown().withDetail("reason", "No sync progress found");
                return;
            }

            EthBlockNumber ethBlockNumber = web3jProvider.sendWithFailover(
                    web3j -> web3j.ethBlockNumber().send());
            long chainHead = ethBlockNumber.getBlockNumber().longValue();
            long syncedBlock = progress.getLastSyncedBlock();
            long blockDelay = chainHead - syncedBlock;

            builder.withDetail("chainHead", chainHead)
                    .withDetail("syncedBlock", syncedBlock)
                    .withDetail("blockDelay", blockDelay)
                    .withDetail("status", progress.getStatus());

            if (blockDelay > MAX_ACCEPTABLE_DELAY) {
                builder.down();
                alertService.alert("SYNC_DELAY", AlertLevel.CRITICAL,
                        "Block sync delay " + blockDelay + " blocks (threshold " + MAX_ACCEPTABLE_DELAY + ")");
            } else {
                builder.up();
            }
        } catch (Exception e) {
            log.warn("Block sync health check failed: {}", e.getMessage());
            builder.down().withDetail("error", e.getMessage());
        }
    }
}
