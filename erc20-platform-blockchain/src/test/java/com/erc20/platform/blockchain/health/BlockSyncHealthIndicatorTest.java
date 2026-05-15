package com.erc20.platform.blockchain.health;

import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class BlockSyncHealthIndicatorTest {

    @Mock
    private BlockSyncProgressMapper blockSyncProgressMapper;
    @Mock
    private Web3jProvider web3jProvider;
    @Mock
    private AlertService alertService;

    private BlockSyncHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new BlockSyncHealthIndicator(blockSyncProgressMapper, web3jProvider, alertService);
    }

    @Test
    void syncDelayLessThan30_statusUp() throws Exception {
        BlockSyncProgress progress = BlockSyncProgress.builder()
                .chainId(1).lastSyncedBlock(100L).status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        EthBlockNumber ethBlockNumber = new EthBlockNumber();
        ethBlockNumber.setResult("0x6E"); // 110 → delay = 10
        doReturn(ethBlockNumber).when(web3jProvider).sendWithFailover(any());

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(10L, health.getDetails().get("blockDelay"));
    }

    @Test
    void syncDelayGreaterThan30_statusDown() throws Exception {
        BlockSyncProgress progress = BlockSyncProgress.builder()
                .chainId(1).lastSyncedBlock(100L).status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        EthBlockNumber ethBlockNumber = new EthBlockNumber();
        ethBlockNumber.setResult("0x96"); // 150 → delay = 50
        doReturn(ethBlockNumber).when(web3jProvider).sendWithFailover(any());

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(50L, health.getDetails().get("blockDelay"));
    }
}
