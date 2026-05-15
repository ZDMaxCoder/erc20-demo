package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.dal.mapper.BlockRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.domain.entity.BlockRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockSyncEngineTest {

    @Mock
    private Web3jProvider web3jProvider;
    @Mock
    private BlockRecordMapper blockRecordMapper;
    @Mock
    private BlockSyncProgressMapper blockSyncProgressMapper;
    @Mock
    private ReorgHandler reorgHandler;
    @Mock
    private TransferEventExtractor transferEventExtractor;
    @Mock
    private BlockEventPublisher blockEventPublisher;
    @Mock
    private BusinessMetrics businessMetrics;

    private BlockSyncProperties properties;
    private BlockSyncEngine blockSyncEngine;

    private static final int CHAIN_ID = 1;

    @BeforeEach
    void setUp() {
        properties = new BlockSyncProperties();
        properties.setChainId(CHAIN_ID);
        properties.setBatchSize(5);
        blockSyncEngine = new BlockSyncEngine(web3jProvider, blockRecordMapper,
                blockSyncProgressMapper, reorgHandler, transferEventExtractor,
                blockEventPublisher, properties, businessMetrics);
    }

    // --- Task 4.1: happy path — block with correct parentHash saved, progress updated ---

    @Test
    void syncBlock_correctParentHash_blockSavedAndProgressUpdated() throws Exception {
        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(100L).lastSyncedBlockHash("hash100").status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        // First block syncs, second returns null (end of batch)
        EthBlock ethBlock = mockEthBlock(101L, "hash101", "hash100", 5);
        EthBlock emptyBlock = new EthBlock();
        emptyBlock.setResult(null);
        doReturn(ethBlock).doReturn(emptyBlock).when(web3jProvider).sendWithFailover(any());

        doReturn(Collections.emptyList()).when(transferEventExtractor).extractFromBlock(eq(101L), any());

        blockSyncEngine.syncNextBatch();

        ArgumentCaptor<BlockRecord> blockCaptor = ArgumentCaptor.forClass(BlockRecord.class);
        verify(blockRecordMapper).insert(blockCaptor.capture());
        BlockRecord savedBlock = blockCaptor.getValue();
        assertEquals(101L, savedBlock.getBlockNumber().longValue());
        assertEquals("hash101", savedBlock.getBlockHash());
        assertEquals("hash100", savedBlock.getParentHash());
        assertEquals(5, savedBlock.getTxCount().intValue());

        verify(blockSyncProgressMapper).updateById(any(BlockSyncProgress.class));
        assertEquals(101L, progress.getLastSyncedBlock().longValue());
        assertEquals("hash101", progress.getLastSyncedBlockHash());
    }

    // --- Task 4.2: parentHash mismatch → ReorgHandler invoked ---

    @Test
    void syncBlock_parentHashMismatch_reorgHandlerInvoked() throws Exception {
        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(100L).lastSyncedBlockHash("hash100").status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        EthBlock ethBlock = mockEthBlock(101L, "hash101", "wrongParent", 3);
        doReturn(ethBlock).when(web3jProvider).sendWithFailover(any());

        doReturn(99L).when(reorgHandler).handleReorg(101L, "hash100", "wrongParent");

        blockSyncEngine.syncNextBatch();

        verify(reorgHandler).handleReorg(101L, "hash100", "wrongParent");
        verify(blockRecordMapper, never()).insert(any());
    }

    // --- Task 4.3: RPC returns null block → no action ---

    @Test
    void syncBlock_rpcReturnsNull_noAction() throws Exception {
        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(100L).lastSyncedBlockHash("hash100").status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        EthBlock ethBlock = new EthBlock();
        ethBlock.setResult(null);
        doReturn(ethBlock).when(web3jProvider).sendWithFailover(any());

        blockSyncEngine.syncNextBatch();

        verify(blockRecordMapper, never()).insert(any());
        verify(blockSyncProgressMapper, never()).updateById(any());
        verify(reorgHandler, never()).handleReorg(anyLong(), anyString(), anyString());
    }

    // --- Helper ---

    @SuppressWarnings("unchecked")
    private EthBlock mockEthBlock(long number, String hash, String parentHash, int txCount) {
        EthBlock ethBlock = new EthBlock();
        EthBlock.Block block = mock(EthBlock.Block.class);
        lenient().doReturn(BigInteger.valueOf(number)).when(block).getNumber();
        lenient().doReturn(hash).when(block).getHash();
        doReturn(parentHash).when(block).getParentHash();
        lenient().doReturn(BigInteger.valueOf(System.currentTimeMillis() / 1000)).when(block).getTimestamp();
        List txList = new ArrayList();
        for (int i = 0; i < txCount; i++) {
            txList.add(new Object());
        }
        lenient().doReturn(txList).when(block).getTransactions();
        ethBlock.setResult(block);
        return ethBlock;
    }
}
