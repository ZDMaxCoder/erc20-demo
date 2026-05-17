package com.erc20.platform.blockchain.adapter.model;

import com.erc20.platform.blockchain.erc20.TransferEvent;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransferResultTest {

    @Test
    void builder_successWithConsistentAmounts_isAmountConsistentReturnsTrue() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash("0xabc")
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000))
                .expectedAmount(BigInteger.valueOf(1000))
                .build();

        assertEquals(TransferOutcome.SUCCESS, result.getOutcome());
        assertTrue(result.isAmountConsistent());
    }

    @Test
    void builder_anomalyWithAmountMismatch_isAmountConsistentReturnsFalse() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.ANOMALY)
                .txHash("0xabc")
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(990))
                .expectedAmount(BigInteger.valueOf(1000))
                .anomalyReason("fee detected")
                .build();

        assertFalse(result.isAmountConsistent());
        assertEquals("fee detected", result.getAnomalyReason());
    }

    @Test
    void builder_pendingResult_blockNumberAndActualAmountAreNull() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.PENDING)
                .txHash("0xabc")
                .build();

        assertNull(result.getBlockNumber());
        assertNull(result.getActualAmount());
    }

    @Test
    void failed_withTxHashAndReason_outcomeIsFailedAndReasonSet() {
        TransferResult result = TransferResult.failed("0xdef", "Transfer event not found");

        assertEquals(TransferOutcome.FAILED, result.getOutcome());
        assertEquals("0xdef", result.getTxHash());
        assertEquals("Transfer event not found", result.getAnomalyReason());
    }

    @Test
    void hasBalanceDiffAnomaly_actualAmountAndBalanceDiffDiffer_returnsTrue() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.ANOMALY)
                .txHash("0xabc")
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000))
                .expectedAmount(BigInteger.valueOf(1000))
                .balanceDiff(BigInteger.valueOf(990))
                .build();

        assertTrue(result.hasBalanceDiffAnomaly());
    }

    @Test
    void hasBalanceDiffAnomaly_nullBalanceDiff_returnsFalse() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash("0xabc")
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000))
                .expectedAmount(BigInteger.valueOf(1000))
                .build();

        assertFalse(result.hasBalanceDiffAnomaly());
    }

    @Test
    void pending_factoryMethod_createsPendingResult() {
        TransferResult result = TransferResult.pending("0xghi");

        assertEquals(TransferOutcome.PENDING, result.getOutcome());
        assertEquals("0xghi", result.getTxHash());
        assertNull(result.getBlockNumber());
        assertNull(result.getActualAmount());
    }

    @Test
    void builder_withEvents_eventsAreUnmodifiable() {
        TransferEvent event = TransferEvent.builder()
                .contractAddress("0xtoken")
                .from("0xfrom")
                .to("0xto")
                .value(BigInteger.valueOf(1000))
                .txHash("0xabc")
                .blockNumber(100L)
                .logIndex(0)
                .build();

        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash("0xabc")
                .blockNumber(100L)
                .actualAmount(BigInteger.valueOf(1000))
                .expectedAmount(BigInteger.valueOf(1000))
                .events(Collections.singletonList(event))
                .build();

        List<TransferEvent> events = result.getEvents();
        assertNotNull(events);
        assertEquals(1, events.size());
        assertThrows(UnsupportedOperationException.class, () -> events.add(event));
    }

    @Test
    void toString_containsKeyFields() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash("0xabc")
                .blockNumber(100L)
                .build();

        String str = result.toString();
        assertTrue(str.contains("SUCCESS"));
        assertTrue(str.contains("0xabc"));
    }

    @Test
    void isAmountConsistent_bothNull_returnsTrue() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.PENDING)
                .txHash("0xabc")
                .build();

        assertTrue(result.isAmountConsistent());
    }

    @Test
    void isAmountConsistent_oneNull_returnsFalse() {
        TransferResult result = TransferResult.builder()
                .outcome(TransferOutcome.ANOMALY)
                .txHash("0xabc")
                .actualAmount(BigInteger.valueOf(1000))
                .build();

        assertFalse(result.isAmountConsistent());
    }
}
