package com.erc20.platform.blockchain.adapter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransferOutcomeTest {

    @Test
    void values_allFourStates_shouldExist() {
        TransferOutcome[] values = TransferOutcome.values();
        assertEquals(4, values.length);
    }

    @Test
    void valueOf_eachNamedConstant_shouldResolve() {
        assertNotNull(TransferOutcome.valueOf("SUCCESS"));
        assertNotNull(TransferOutcome.valueOf("FAILED"));
        assertNotNull(TransferOutcome.valueOf("PENDING"));
        assertNotNull(TransferOutcome.valueOf("ANOMALY"));
    }

    @Test
    void values_shouldBeInExpectedOrder() {
        TransferOutcome[] values = TransferOutcome.values();
        assertEquals(TransferOutcome.SUCCESS, values[0]);
        assertEquals(TransferOutcome.FAILED, values[1]);
        assertEquals(TransferOutcome.PENDING, values[2]);
        assertEquals(TransferOutcome.ANOMALY, values[3]);
    }
}
