package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.erc20.ERC20TransferEventParser;
import com.erc20.platform.blockchain.erc20.TransferEvent;
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
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    @Mock private ERC20TransferEventParser transferEventParser;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private Request<?, EthGetTransactionReceipt> receiptRequest;

    private TransactionConfirmTracker tracker;

    private static final int CHAIN_ID = 1;
    private static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String FROM = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        tracker = new TransactionConfirmTracker(
                transactionRecordMapper, nonceManager, web3j, rocketMQTemplate,
                transferEventParser, tokenConfigMapper, CHAIN_ID);
    }

    // --- 8.1 PENDING tx with receipt status=1 + Transfer event → CONFIRMED, confirmNonce called ---

    @Test
    void scanPending_receiptConfirmed_updatesToConfirmedAndCallsConfirmNonce() throws Exception {
        TransactionRecord pendingTx = buildPendingTx();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT_ADDRESS)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(TOKEN_ID);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash("0xblockhash");
        receipt.setGasUsed("0x5208");
        receipt.setLogs(new ArrayList<>());

        List<TransferEvent> events = Collections.singletonList(
                TransferEvent.builder()
                        .contractAddress(CONTRACT_ADDRESS)
                        .from(FROM)
                        .to("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                        .value(BigInteger.valueOf(1000000))
                        .txHash(TX_HASH)
                        .blockNumber(100L)
                        .logIndex(0)
                        .build());
        doReturn(events).when(transferEventParser).parseFromReceipt(receipt, CONTRACT_ADDRESS);

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
        TransactionRecord pendingTx = buildPendingTx();

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
        TransactionRecord pendingTx = buildPendingTx();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt(TX_HASH);
        doReturn(receiptResponse).when(receiptRequest).send();

        tracker.scanPendingTransactions();

        verify(transactionRecordMapper, never()).updateById(any());
        verify(nonceManager, never()).confirmNonce(anyInt(), anyString(), anyLong());
    }

    // --- 3.2 Receipt 0x1 with Transfer event → CONFIRMED with actualAmount ---

    @Test
    void checkConfirmation_receiptSuccessWithTransferEvent_confirmedWithActualAmount() throws Exception {
        TransactionRecord pendingTx = buildPendingTx();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT_ADDRESS)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(TOKEN_ID);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash("0xblockhash");
        receipt.setGasUsed("0x5208");
        receipt.setLogs(new ArrayList<>());

        BigInteger expectedAmount = BigInteger.valueOf(1000000);
        List<TransferEvent> events = Collections.singletonList(
                TransferEvent.builder()
                        .contractAddress(CONTRACT_ADDRESS)
                        .from(FROM)
                        .to("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                        .value(expectedAmount)
                        .txHash(TX_HASH)
                        .blockNumber(100L)
                        .logIndex(0)
                        .build());
        doReturn(events).when(transferEventParser).parseFromReceipt(receipt, CONTRACT_ADDRESS);

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt(TX_HASH);
        doReturn(receiptResponse).when(receiptRequest).send();

        tracker.scanPendingTransactions();

        ArgumentCaptor<TxStatusChangedMessage> msgCaptor = ArgumentCaptor.forClass(TxStatusChangedMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("tx-status-changed"), msgCaptor.capture());
        TxStatusChangedMessage message = msgCaptor.getValue();
        assertEquals(TxStatus.CONFIRMED.getCode(), message.getToStatus());
        assertEquals(expectedAmount, message.getActualAmount());
    }

    // --- 3.3 Receipt 0x1 without Transfer event → FAILED ---

    @Test
    void checkConfirmation_receiptSuccessNoTransferEvent_markedFailed() throws Exception {
        TransactionRecord pendingTx = buildPendingTx();

        doReturn(Collections.singletonList(pendingTx))
                .when(transactionRecordMapper).selectList(any());

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT_ADDRESS)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(TOKEN_ID);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x64");
        receipt.setBlockHash("0xblockhash");
        receipt.setGasUsed("0x5208");
        receipt.setLogs(new ArrayList<>());

        doReturn(Collections.emptyList()).when(transferEventParser).parseFromReceipt(receipt, CONTRACT_ADDRESS);

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt(TX_HASH);
        doReturn(receiptResponse).when(receiptRequest).send();

        tracker.scanPendingTransactions();

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updated = captor.getValue();
        assertEquals(TxStatus.FAILED.getCode(), updated.getStatus());
        assertEquals("Transfer event not found in receipt", updated.getErrorMessage());

        ArgumentCaptor<TxStatusChangedMessage> msgCaptor = ArgumentCaptor.forClass(TxStatusChangedMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("tx-status-changed"), msgCaptor.capture());
        TxStatusChangedMessage message = msgCaptor.getValue();
        assertEquals(TxStatus.FAILED.getCode(), message.getToStatus());
        assertNull(message.getActualAmount());
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
