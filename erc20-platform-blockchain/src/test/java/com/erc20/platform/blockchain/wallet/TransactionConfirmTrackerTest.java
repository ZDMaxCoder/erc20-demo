package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.ConsecutiveFailureBreaker;
import com.erc20.platform.blockchain.adapter.TransferConfirmer;
import com.erc20.platform.blockchain.adapter.model.TransferOutcome;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.TransactionRecord;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionConfirmTrackerTest {

    @Mock private TransactionRecordMapper transactionRecordMapper;
    @Mock private NonceManager nonceManager;
    @Mock private TransferConfirmer transferConfirmer;
    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private ConsecutiveFailureBreaker consecutiveFailureBreaker;

    private TransactionConfirmTracker tracker;

    private static final int CHAIN_ID = 1;
    private static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String FROM = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        tracker = new TransactionConfirmTracker(
                transactionRecordMapper, nonceManager, transferConfirmer, rocketMQTemplate,
                tokenConfigMapper, consecutiveFailureBreaker, CHAIN_ID);
    }

    @Test
    void scanPending_receiptConfirmed_updatesToConfirmedAndCallsConfirmNonce() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult successResult = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000000))
                .build();
        doReturn(successResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updated = captor.getValue();
        assertEquals(TxStatus.CONFIRMED.getCode(), updated.getStatus());
        assertEquals(100L, updated.getBlockNumber().longValue());

        verify(nonceManager).confirmNonce(CHAIN_ID, FROM, 5L);
    }

    @Test
    void scanPending_receiptFailed_updatesToFailed() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult failedResult = TransferResult.failed(TX_HASH, "receipt status failed");
        doReturn(failedResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updated = captor.getValue();
        assertEquals(TxStatus.FAILED.getCode(), updated.getStatus());

        verify(nonceManager, never()).confirmNonce(anyInt(), anyString(), anyLong());
    }

    @Test
    void scanPending_noReceipt_staysPending() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult pendingResult = TransferResult.pending(TX_HASH);
        doReturn(pendingResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        verify(transactionRecordMapper, never()).updateById(any());
        verify(nonceManager, never()).confirmNonce(anyInt(), anyString(), anyLong());
        verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(TxStatusChangedMessage.class));
    }

    @Test
    void checkConfirmation_receiptSuccessWithTransferEvent_confirmedWithActualAmount() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        BigInteger expectedAmount = BigInteger.valueOf(1000000);
        TransferResult successResult = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(expectedAmount)
                .build();
        doReturn(successResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        ArgumentCaptor<TxStatusChangedMessage> msgCaptor = ArgumentCaptor.forClass(TxStatusChangedMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("tx-status-changed"), msgCaptor.capture());
        TxStatusChangedMessage message = msgCaptor.getValue();
        assertEquals(TxStatus.CONFIRMED.getCode(), message.getToStatus());
        assertEquals(expectedAmount, message.getActualAmount());
    }

    @Test
    void checkConfirmation_receiptSuccessNoTransferEvent_markedFailed() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult failedResult = TransferResult.failed(TX_HASH, "Transfer event not found");
        doReturn(failedResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updated = captor.getValue();
        assertEquals(TxStatus.FAILED.getCode(), updated.getStatus());
        assertEquals("Transfer event not found", updated.getErrorMessage());

        ArgumentCaptor<TxStatusChangedMessage> msgCaptor = ArgumentCaptor.forClass(TxStatusChangedMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("tx-status-changed"), msgCaptor.capture());
        TxStatusChangedMessage message = msgCaptor.getValue();
        assertEquals(TxStatus.FAILED.getCode(), message.getToStatus());
        assertNull(message.getActualAmount());
    }

    @Test
    void checkConfirmation_anomaly_publishesMessageWithAnomalyFlag() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult anomalyResult = TransferResult.builder()
                .outcome(TransferOutcome.ANOMALY)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(900000))
                .anomalyReason("Amount mismatch: expected 1000000, actual 900000")
                .build();
        doReturn(anomalyResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> txCaptor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(txCaptor.capture());
        assertEquals(TxStatus.CONFIRMED.getCode(), txCaptor.getValue().getStatus());

        verify(nonceManager).confirmNonce(CHAIN_ID, FROM, 5L);

        ArgumentCaptor<TxStatusChangedMessage> msgCaptor = ArgumentCaptor.forClass(TxStatusChangedMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("tx-status-changed"), msgCaptor.capture());
        TxStatusChangedMessage message = msgCaptor.getValue();
        assertEquals(TxStatus.CONFIRMED.getCode(), message.getToStatus());
        assertEquals(BigInteger.valueOf(900000), message.getActualAmount());
        assertTrue(message.isAnomaly());
        assertEquals("Amount mismatch: expected 1000000, actual 900000", message.getAnomalyReason());
    }

    @Test
    void checkConfirmation_tokenWithDepositConfirmBlocks_passesMinConfirmations() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT_ADDRESS)
                .depositConfirmBlocks(12)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(TOKEN_ID);

        TransferResult successResult = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000000))
                .build();
        doReturn(successResult).when(transferConfirmer)
                .confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(12));

        tracker.scanPendingTransactions();

        verify(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(12));
        verify(transactionRecordMapper).updateById(any());
    }

    @Test
    void checkConfirmation_nullDepositConfirmBlocks_passesZeroMinConfirmations() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult successResult = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000000))
                .build();
        doReturn(successResult).when(transferConfirmer)
                .confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        verify(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));
    }

    @Test
    void checkConfirmation_success_publishesMessageWithAnomalyFalse() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult successResult = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000000))
                .build();
        doReturn(successResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        ArgumentCaptor<TxStatusChangedMessage> msgCaptor = ArgumentCaptor.forClass(TxStatusChangedMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("tx-status-changed"), msgCaptor.capture());
        TxStatusChangedMessage message = msgCaptor.getValue();
        assertFalse(message.isAnomaly());
        assertNull(message.getAnomalyReason());
    }

    @Test
    void checkConfirmation_success_callsBreakerRecordSuccess() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult successResult = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000000))
                .build();
        doReturn(successResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        verify(consecutiveFailureBreaker).recordSuccess(CONTRACT_ADDRESS);
        verify(consecutiveFailureBreaker, never()).recordFailure(anyString());
    }

    @Test
    void checkConfirmation_failed_callsBreakerRecordFailure() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult failedResult = TransferResult.failed(TX_HASH, "receipt status failed");
        doReturn(failedResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        verify(consecutiveFailureBreaker).recordFailure(CONTRACT_ADDRESS);
        verify(consecutiveFailureBreaker, never()).recordSuccess(anyString());
    }

    @Test
    void checkConfirmation_anomaly_doesNotCallBreaker() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult anomalyResult = TransferResult.builder()
                .outcome(TransferOutcome.ANOMALY)
                .txHash(TX_HASH)
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(900000))
                .anomalyReason("Amount mismatch")
                .build();
        doReturn(anomalyResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        verify(consecutiveFailureBreaker, never()).recordSuccess(anyString());
        verify(consecutiveFailureBreaker, never()).recordFailure(anyString());
    }

    @Test
    void checkConfirmation_pending_doesNotCallBreaker() {
        TransactionRecord pendingTx = buildPendingTx();
        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());
        stubTokenConfig();

        TransferResult pendingResult = TransferResult.pending(TX_HASH);
        doReturn(pendingResult).when(transferConfirmer).confirm(eq(TX_HASH), eq(CONTRACT_ADDRESS), any(), any(), eq(0));

        tracker.scanPendingTransactions();

        verify(consecutiveFailureBreaker, never()).recordSuccess(anyString());
        verify(consecutiveFailureBreaker, never()).recordFailure(anyString());
    }

    private void stubTokenConfig() {
        TokenConfig tokenConfig = TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT_ADDRESS)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(TOKEN_ID);
    }

    private TransactionRecord buildPendingTx() {
        return TransactionRecord.builder()
                .id(1L)
                .txHash(TX_HASH)
                .fromAddress(FROM)
                .chainId(CHAIN_ID)
                .nonce(5L)
                .tokenId(TOKEN_ID)
                .status(TxStatus.PENDING.getCode())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
    }
}
