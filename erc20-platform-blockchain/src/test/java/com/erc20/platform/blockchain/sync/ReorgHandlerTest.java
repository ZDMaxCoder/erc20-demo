package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.BlockRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.BlockRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.AlertService;
import com.erc20.platform.service.DepositService;
import com.erc20.platform.service.WithdrawService;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private TransactionRecordMapper transactionRecordMapper;
    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;
    @Mock
    private AlertService alertService;
    @Mock
    private Web3jProvider web3jProvider;
    @Mock
    private BusinessMetrics businessMetrics;
    @Mock
    private DepositService depositService;
    @Mock
    private WithdrawService withdrawService;

    private BlockSyncProperties properties;
    private ReorgHandler reorgHandler;

    private static final int CHAIN_ID = 1;

    @BeforeEach
    void setUp() {
        properties = new BlockSyncProperties();
        properties.setChainId(CHAIN_ID);
        properties.setMaxReorgDepth(50);
        reorgHandler = new ReorgHandler(blockRecordMapper, blockSyncProgressMapper,
                depositRecordMapper, alertService, web3jProvider, properties, businessMetrics,
                depositService, withdrawService, withdrawRecordMapper, transactionRecordMapper);
    }

    // --- Existing: reorg detected, handler finds fork point ---

    @Test
    void handleReorg_parentHashMismatch_findsForkPoint() throws IOException {
        BlockRecord block101 = BlockRecord.builder()
                .blockNumber(101L).blockHash("hashB").parentHash("hashA").chainId(CHAIN_ID).build();
        BlockRecord block100 = BlockRecord.builder()
                .blockNumber(100L).blockHash("hashA").parentHash("hash99").chainId(CHAIN_ID).build();

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

        verify(blockRecordMapper).update(eq(null), any());

        ArgumentCaptor<BlockSyncProgress> progressCaptor = ArgumentCaptor.forClass(BlockSyncProgress.class);
        verify(blockSyncProgressMapper).updateById(progressCaptor.capture());
        assertEquals(100L, progressCaptor.getValue().getLastSyncedBlock().longValue());
        assertEquals("hashA", progressCaptor.getValue().getLastSyncedBlockHash());

        verify(alertService).alert(eq("REORG"), eq(AlertLevel.WARN), anyString());
    }

    // --- Existing: reorg depth exceeds maxReorgDepth → throws exception ---

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

        ArgumentCaptor<BlockSyncProgress> progressCaptor = ArgumentCaptor.forClass(BlockSyncProgress.class);
        verify(blockSyncProgressMapper).updateById(progressCaptor.capture());
        assertEquals("ERROR", progressCaptor.getValue().getStatus());

        verify(alertService).alert(eq("REORG"), eq(AlertLevel.CRITICAL), anyString());
    }

    // --- Existing: after reorg, deposits marked reorged ---

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

        verify(blockRecordMapper).update(eq(null), any());
    }

    // --- Task 2.1: verify handleReorg calls DepositService.handleReorg() ---

    @Test
    void handleReorg_callsDepositServiceHandleReorg_withAffectedBlockNumbers() throws IOException {
        long forkPoint = setupReorgScenario(100L, 102L);

        assertEquals(100L, forkPoint);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> blockNumbersCaptor = ArgumentCaptor.forClass(List.class);
        verify(depositService).handleReorg(blockNumbersCaptor.capture());

        List<Long> affectedBlocks = blockNumbersCaptor.getValue();
        assertEquals(1, affectedBlocks.size());
        assertTrue(affectedBlocks.contains(101L));
    }

    // --- Task 2.2: verify handleReorg queries confirmed withdrawals and calls revertConfirmedWithdraw ---

    @Test
    void handleReorg_revertsConfirmedWithdrawals_inReorgRange() throws IOException {
        TransactionRecord txRecord = TransactionRecord.builder()
                .id(1L)
                .txHash("0xtx1")
                .status(TxStatus.CONFIRMED.getCode())
                .bizType("WITHDRAW")
                .bizId(10L)
                .blockNumber(101L)
                .build();

        doReturn(Collections.singletonList(txRecord))
                .when(transactionRecordMapper).selectList(any());

        long forkPoint = setupReorgScenario(100L, 102L);

        assertEquals(100L, forkPoint);
        verify(withdrawService).revertConfirmedWithdraw(10L);
    }

    @Test
    void handleReorg_noConfirmedWithdrawalsInRange_noWithdrawalRevert() throws IOException {
        doReturn(Collections.emptyList())
                .when(transactionRecordMapper).selectList(any());

        long forkPoint = setupReorgScenario(100L, 102L);

        assertEquals(100L, forkPoint);
        verify(withdrawService, never()).revertConfirmedWithdraw(anyLong());
    }

    // --- Task 2.3: verify TransactionRecords with CONFIRMED status above forkPoint are reset to PENDING ---

    @Test
    void handleReorg_resetsConfirmedTransactionRecords_toPending() throws IOException {
        TransactionRecord txRecord = TransactionRecord.builder()
                .id(1L)
                .txHash("0xtx1")
                .status(TxStatus.CONFIRMED.getCode())
                .bizType("COLLECTION")
                .bizId(20L)
                .blockNumber(101L)
                .blockHash("hash101")
                .build();

        doReturn(Collections.singletonList(txRecord))
                .when(transactionRecordMapper).selectList(any());

        long forkPoint = setupReorgScenario(100L, 102L);

        assertEquals(100L, forkPoint);

        verify(transactionRecordMapper).update(eq(null), any());
    }

    // --- Helper: sets up a standard reorg scenario and returns forkPoint ---

    private long setupReorgScenario(long forkBlockNumber, long currentBlockNumber) throws IOException {
        long backtrackStart = currentBlockNumber - 1;

        BlockRecord reorgedBlock = BlockRecord.builder()
                .blockNumber(backtrackStart).blockHash("hashReorged").parentHash("hashPrev").chainId(CHAIN_ID).build();
        BlockRecord forkBlock = BlockRecord.builder()
                .blockNumber(forkBlockNumber).blockHash("hashFork").parentHash("hash" + (forkBlockNumber - 1)).chainId(CHAIN_ID).build();

        doReturn(reorgedBlock).doReturn(forkBlock).doReturn(forkBlock)
                .when(blockRecordMapper).selectOne(any());

        EthBlock chainBlockReorged = mockEthBlock("newHashReorged");
        EthBlock chainBlockFork = mockEthBlock("hashFork");

        doReturn(chainBlockReorged).doReturn(chainBlockFork)
                .when(web3jProvider).sendWithFailover(any());

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .id(1L).chainId(CHAIN_ID).lastSyncedBlock(backtrackStart).lastSyncedBlockHash("hashReorged").status("RUNNING").build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any());

        return reorgHandler.handleReorg(currentBlockNumber, "hashReorged", "invalidParent");
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
