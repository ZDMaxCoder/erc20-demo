package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.model.TransferOutcome;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import com.erc20.platform.blockchain.erc20.ERC20TransferEventParser;
import com.erc20.platform.blockchain.erc20.TransferEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferConfirmerTest {

    private static final String TX_HASH = "0xabc123";
    private static final String CONTRACT = "0x1234567890abcdef1234567890abcdef12345678";
    private static final BigInteger EXPECTED_AMOUNT = BigInteger.valueOf(1000);
    private static final String TO_ADDRESS = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Mock
    private Web3j web3j;

    @Mock
    private ERC20TransferEventParser eventParser;

    @Mock
    private TokenRiskProfileRegistry tokenRiskProfileRegistry;

    @Mock
    private BalanceDiffChecker balanceDiffChecker;

    @SuppressWarnings("rawtypes")
    @Mock
    private Request request;

    @SuppressWarnings("rawtypes")
    @Mock
    private Request blockNumberRequest;

    private TransferConfirmer confirmer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        confirmer = new TransferConfirmer(web3j, eventParser, tokenRiskProfileRegistry, balanceDiffChecker);
        when(web3j.ethGetTransactionReceipt(anyString())).thenReturn(request);
    }

    @Test
    void confirm_noReceipt_shouldReturnPending() throws Exception {
        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(null);
        when(request.send()).thenReturn(response);

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS);

        assertEquals(TransferOutcome.PENDING, result.getOutcome());
        assertEquals(TX_HASH, result.getTxHash());
        assertNull(result.getBlockNumber());
    }

    @Test
    void confirm_receiptStatusFailed_shouldReturnFailed() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x0");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS);

        assertEquals(TransferOutcome.FAILED, result.getOutcome());
        assertEquals(TX_HASH, result.getTxHash());
    }

    @Test
    void confirm_noTransferEvent_shouldReturnFailed() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.<TransferEvent>emptyList());

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS);

        assertEquals(TransferOutcome.FAILED, result.getOutcome());
        assertTrue(result.getAnomalyReason().contains("Transfer event not found"));
    }

    @Test
    void confirm_amountMatch_shouldReturnSuccess() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS);

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertEquals(BigInteger.valueOf(1000), result.getActualAmount());
        assertEquals(EXPECTED_AMOUNT, result.getExpectedAmount());
        assertTrue(result.isAmountConsistent());
        assertEquals(12345L, result.getBlockNumber().longValue());
        assertEquals(1, result.getEvents().size());
    }

    @Test
    void confirm_amountMismatch_shouldReturnAnomaly() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(990))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS);

        assertEquals(TransferOutcome.ANOMALY, result.getOutcome());
        assertEquals(BigInteger.valueOf(990), result.getActualAmount());
        assertFalse(result.isAmountConsistent());
        assertTrue(result.getAnomalyReason().contains("expected=1000"));
        assertTrue(result.getAnomalyReason().contains("actual=990"));
    }

    @Test
    void confirm_feeOnTransferWithBalanceDiff_shouldReturnAnomaly() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(EnumSet.of(TokenCapability.FEE_ON_TRANSFER))
                .riskLevel(RiskLevel.MEDIUM)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(profile);

        when(balanceDiffChecker.queryBalance(CONTRACT, TO_ADDRESS))
                .thenReturn(BigInteger.valueOf(1950));
        when(balanceDiffChecker.computeDiff(BigInteger.valueOf(1000), BigInteger.valueOf(1950)))
                .thenReturn(BigInteger.valueOf(950));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, BigInteger.valueOf(1000));

        assertEquals(TransferOutcome.ANOMALY, result.getOutcome());
        assertEquals(BigInteger.valueOf(950), result.getActualAmount());
        assertEquals(BigInteger.valueOf(950), result.getBalanceDiff());
        assertTrue(result.getAnomalyReason().contains("Balance diff mismatch"));
    }

    @Test
    void confirm_multipleEventsSum_shouldReturnSuccess() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event1 = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(600))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        TransferEvent event2 = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(400))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(1)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Arrays.asList(event1, event2));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS);

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertEquals(BigInteger.valueOf(1000), result.getActualAmount());
        assertTrue(result.isAmountConsistent());
        assertEquals(2, result.getEvents().size());
    }

    @Test
    void confirm_standardTokenWithBalanceBefore_shouldSkipBalanceDiff() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(profile);

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, BigInteger.valueOf(500));

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertNull(result.getBalanceDiff());
        verify(balanceDiffChecker, never()).queryBalance(anyString(), anyString());
    }

    @Test
    void confirm_balanceDiffMatchesExpected_shouldReturnSuccess() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.HIGH)
                .admissionPassed(true)
                .autoProcessingAllowed(false)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(profile);

        when(balanceDiffChecker.queryBalance(CONTRACT, TO_ADDRESS))
                .thenReturn(BigInteger.valueOf(2000));
        when(balanceDiffChecker.computeDiff(BigInteger.valueOf(1000), BigInteger.valueOf(2000)))
                .thenReturn(BigInteger.valueOf(1000));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, BigInteger.valueOf(1000));

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertEquals(BigInteger.valueOf(1000), result.getBalanceDiff());
        assertEquals(BigInteger.valueOf(1000), result.getActualAmount());
    }

    @Test
    void confirm_balanceBeforeNull_shouldSkipFourthLayer() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3039");

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(12345L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, null);

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        verify(tokenRiskProfileRegistry, never()).getProfile(anyString());
        verify(balanceDiffChecker, never()).queryBalance(anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void confirm_insufficientConfirmations_shouldReturnPending() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3e8"); // 1000

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(1000L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        when(web3j.ethBlockNumber()).thenReturn(blockNumberRequest);
        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x3ed"); // 1005, confirmations = 5 < 12
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, 12);

        assertEquals(TransferOutcome.PENDING, result.getOutcome());
        assertEquals(TX_HASH, result.getTxHash());
    }

    @SuppressWarnings("unchecked")
    @Test
    void confirm_sufficientConfirmations_shouldReturnSuccess() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3e8"); // 1000

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(1000L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        when(web3j.ethBlockNumber()).thenReturn(blockNumberRequest);
        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x3f4"); // 1012, confirmations = 12 >= 12
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, 12);

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertEquals(BigInteger.valueOf(1000), result.getActualAmount());
        assertEquals(EXPECTED_AMOUNT, result.getExpectedAmount());
        assertTrue(result.isAmountConsistent());
    }

    @Test
    void confirm_zeroMinConfirmations_shouldSkipConfirmationCheck() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x3e8"); // 1000

        EthGetTransactionReceipt response = new EthGetTransactionReceipt();
        response.setResult(receipt);
        when(request.send()).thenReturn(response);

        TransferEvent event = TransferEvent.builder()
                .contractAddress(CONTRACT)
                .from("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .to(TO_ADDRESS)
                .value(BigInteger.valueOf(1000))
                .txHash(TX_HASH)
                .blockNumber(1000L)
                .logIndex(0)
                .build();
        when(eventParser.parseFromReceipt(any(TransactionReceipt.class), anyString()))
                .thenReturn(Collections.singletonList(event));

        TransferResult result = confirmer.confirm(TX_HASH, CONTRACT, EXPECTED_AMOUNT, TO_ADDRESS, 0);

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertEquals(BigInteger.valueOf(1000), result.getActualAmount());
        verify(web3j, never()).ethBlockNumber();
    }
}
