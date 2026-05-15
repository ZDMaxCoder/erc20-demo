package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TransactionRecord;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionConfirmTrackerTest {

    @Mock private TransactionRecordMapper transactionRecordMapper;
    @Mock private NonceManager nonceManager;
    @Mock private Web3j web3j;
    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private Request<?, EthGetTransactionReceipt> receiptRequest;

    private TransactionConfirmTracker tracker;

    private static final int CHAIN_ID = 1;
    private static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String FROM = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @BeforeEach
    void setUp() {
        tracker = new TransactionConfirmTracker(
                transactionRecordMapper, nonceManager, web3j, rocketMQTemplate, CHAIN_ID);
    }

    // --- 8.1 PENDING tx with receipt status=1 → CONFIRMED, confirmNonce called ---

    @Test
    void scanPending_receiptConfirmed_updatesToConfirmedAndCallsConfirmNonce() throws Exception {
        TransactionRecord pendingTx = TransactionRecord.builder()
                .id(1L)
                .txHash(TX_HASH)
                .fromAddress(FROM)
                .chainId(CHAIN_ID)
                .nonce(5L)
                .status(TxStatus.PENDING.getCode())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash("0xblockhash");
        receipt.setGasUsed("0x5208");

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt(TX_HASH);
        doReturn(receiptResponse).when(receiptRequest).send();

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updated = captor.getValue();
        assertEquals(TxStatus.CONFIRMED.getCode(), updated.getStatus());
        assertEquals(100L, updated.getBlockNumber().longValue());

        verify(nonceManager).confirmNonce(CHAIN_ID, FROM, 5L);
    }

    // --- 8.2 Receipt status=0 → FAILED ---

    @Test
    void scanPending_receiptFailed_updatesToFailed() throws Exception {
        TransactionRecord pendingTx = TransactionRecord.builder()
                .id(1L)
                .txHash(TX_HASH)
                .fromAddress(FROM)
                .chainId(CHAIN_ID)
                .nonce(5L)
                .status(TxStatus.PENDING.getCode())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x0");
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash("0xblockhash");
        receipt.setGasUsed("0x5208");

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt(TX_HASH);
        doReturn(receiptResponse).when(receiptRequest).send();

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updated = captor.getValue();
        assertEquals(TxStatus.FAILED.getCode(), updated.getStatus());

        verify(nonceManager, never()).confirmNonce(anyInt(), anyString(), anyLong());
    }

    // --- 8.3 No receipt → stays PENDING ---

    @Test
    void scanPending_noReceipt_staysPending() throws Exception {
        TransactionRecord pendingTx = TransactionRecord.builder()
                .id(1L)
                .txHash(TX_HASH)
                .fromAddress(FROM)
                .chainId(CHAIN_ID)
                .nonce(5L)
                .status(TxStatus.PENDING.getCode())
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt(TX_HASH);
        doReturn(receiptResponse).when(receiptRequest).send();

        tracker.scanPendingTransactions();

        verify(transactionRecordMapper, never()).updateById(any());
        verify(nonceManager, never()).confirmNonce(anyInt(), anyString(), anyLong());
    }
}
