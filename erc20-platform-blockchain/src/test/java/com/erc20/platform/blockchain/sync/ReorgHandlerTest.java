package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.BlockRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.domain.entity.BlockRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.service.AlertService;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReorgHandlerTest {

    @Mock
    private BlockRecordMapper blockRecordMapper;
    @Mock
    private BlockSyncProgressMapper blockSyncProgressMapper;
    @Mock
    private DepositRecordMapper depositRecordMapper;
    @Mock
    private AlertService alertService;
    @Mock
    private Web3jProvider web3jProvider;
    @Mock
    private BusinessMetrics businessMetrics;

    private BlockSyncProperties properties;
    private ReorgHandler reorgHandler;

    private static final int CHAIN_ID = 1;

    @BeforeEach
    void setUp() {
        properties = new BlockSyncProperties();
        properties.setChainId(CHAIN_ID);
        properties.setMaxReorgDepth(50);
        reorgHandler = new ReorgHandler(blockRecordMapper, blockSyncProgressMapper,
                depositRecordMapper, alertService, web3jProvider, properties, businessMetrics);
    }

    // --- Task 2.1: reorg detected, handler finds fork point ---

    @Test
    void handleReorg_parentHashMismatch_findsForkPoint() throws IOException {
        BlockRecord block101 = BlockRecord.builder()
                .blockNumber(101L).blockHash("hashB").parentHash("hashA").chainId(CHAIN_ID).build();
        BlockRecord block100 = BlockRecord.builder()
                .blockNumber(100L).blockHash("hashA").parentHash("hash99").chainId(CHAIN_ID).build();

        // First selectOne for block 101 (backtrack), second for block 100 (backtrack match),
        // third for fork point block lookup
        doReturn(block101).doReturn(block100).doReturn(block100)
                .when(blockRecordMapper).selectOne(any());

        EthBlock chainBlock101 = mockEthBlock("chainHash101");
        EthBlock chainBlock100 = mockEthBlock("hashA");

        doReturn(chainBlock101).doReturn(chainBlock100)
                .when(web3jProvider).sendWithFailover(any());

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(101L).lastSyncedBlockHash("hashB").status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        long forkPoint = reorgHandler.handleReorg(102L, "hashB", "invalidParent");

        assertEquals(100L, forkPoint);

        // Verify blocks after fork point are marked reorged
        verify(blockRecordMapper).update(eq(null), any());

        // Verify progress reset to fork point
        ArgumentCaptor<BlockSyncProgress> progressCaptor = ArgumentCaptor.forClass(BlockSyncProgress.class);
        verify(blockSyncProgressMapper).updateById(progressCaptor.capture());
        assertEquals(100L, progressCaptor.getValue().getLastSyncedBlock().longValue());
        assertEquals("hashA", progressCaptor.getValue().getLastSyncedBlockHash());

        // Verify alert created via AlertService
        verify(alertService).alert(eq("REORG"), eq(AlertLevel.WARN), anyString());
    }

    // --- Task 2.2: reorg depth exceeds maxReorgDepth → throws exception ---

    @Test
    void handleReorg_depthExceedsMax_throwsException() throws IOException {
        properties.setMaxReorgDepth(2);

        BlockRecord block101 = BlockRecord.builder()
                .blockNumber(101L).blockHash("hashB").parentHash("hashA").chainId(CHAIN_ID).build();
        BlockRecord block100 = BlockRecord.builder()
                .blockNumber(100L).blockHash("hashA").parentHash("hash99").chainId(CHAIN_ID).build();

        doReturn(block101).doReturn(block100)
                .when(blockRecordMapper).selectOne(any());

        EthBlock chainBlock101 = mockEthBlock("otherHash101");
        EthBlock chainBlock100 = mockEthBlock("otherHash100");

        doReturn(chainBlock101).doReturn(chainBlock100)
                .when(web3jProvider).sendWithFailover(any());

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(101L).status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> reorgHandler.handleReorg(102L, "hashB", "invalidParent"));

        assertTrue(ex.getMessage().contains("max reorg depth"));

        // Verify progress set to ERROR
        ArgumentCaptor<BlockSyncProgress> progressCaptor = ArgumentCaptor.forClass(BlockSyncProgress.class);
        verify(blockSyncProgressMapper).updateById(progressCaptor.capture());
        assertEquals("ERROR", progressCaptor.getValue().getStatus());

        // Verify CRITICAL alert via AlertService
        verify(alertService).alert(eq("REORG"), eq(AlertLevel.CRITICAL), anyString());
    }

    // --- Task 2.3: after reorg, progress reset and deposits marked reorged ---

    @Test
    void handleReorg_marksAffectedDepositsAsReorged() throws IOException {
        BlockRecord block101 = BlockRecord.builder()
                .blockNumber(101L).blockHash("hashB").parentHash("hashA").chainId(CHAIN_ID).build();
        BlockRecord block100 = BlockRecord.builder()
                .blockNumber(100L).blockHash("hashA").parentHash("hash99").chainId(CHAIN_ID).build();

        doReturn(block101).doReturn(block100).doReturn(block100)
                .when(blockRecordMapper).selectOne(any());

        EthBlock chainBlock101 = mockEthBlock("newHash101");
        EthBlock chainBlock100 = mockEthBlock("hashA");

        doReturn(chainBlock101).doReturn(chainBlock100)
                .when(web3jProvider).sendWithFailover(any());

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(101L).lastSyncedBlockHash("hashB").status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        long forkPoint = reorgHandler.handleReorg(102L, "hashB", "invalidParent");

        assertEquals(100L, forkPoint);

        // Verify deposits after fork point are marked REORGED
        verify(depositRecordMapper).update(eq(null), any());

        // Verify block records updated
        verify(blockRecordMapper).update(eq(null), any());
    }

    // --- Helper ---

    private EthBlock mockEthBlock(String hash) {
        EthBlock ethBlock = new EthBlock();
        EthBlock.Block block = mock(EthBlock.Block.class);
        doReturn(hash).when(block).getHash();
        ethBlock.setResult(block);
        return ethBlock;
    }
}
