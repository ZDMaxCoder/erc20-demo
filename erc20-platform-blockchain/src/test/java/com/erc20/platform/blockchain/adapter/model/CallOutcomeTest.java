package com.erc20.platform.blockchain.adapter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CallOutcomeTest {

    @Test
    void values_shouldContainExactlyFiveOutcomes() {
        CallOutcome[] values = CallOutcome.values();
        assertEquals(5, values.length);
    }

    @Test
    void valueOf_eachNamedConstant_shouldResolve() {
        assertNotNull(CallOutcome.valueOf("SUCCESS"));
        assertNotNull(CallOutcome.valueOf("SUCCESS_NO_RETURN"));
        assertNotNull(CallOutcome.valueOf("RETURNED_FALSE"));
        assertNotNull(CallOutcome.valueOf("REVERTED"));
        assertNotNull(CallOutcome.valueOf("UNKNOWN"));
    }

    @Test
    void values_shouldBeInExpectedOrder() {
        CallOutcome[] values = CallOutcome.values();
        assertEquals(CallOutcome.SUCCESS, values[0]);
        assertEquals(CallOutcome.SUCCESS_NO_RETURN, values[1]);
        assertEquals(CallOutcome.RETURNED_FALSE, values[2]);
        assertEquals(CallOutcome.REVERTED, values[3]);
        assertEquals(CallOutcome.UNKNOWN, values[4]);
    }
}
