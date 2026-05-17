package com.erc20.platform.blockchain.adapter.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallResultTest {

    @Test
    void success_shouldReturnSuccessOutcomeWithBoolTrueRawValue() {
        CallResult result = CallResult.success();

        assertEquals(CallOutcome.SUCCESS, result.getOutcome());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001", result.getRawValue());
    }

    @Test
    void successNoReturn_shouldReturnSuccessNoReturnOutcomeWithNullRawValue() {
        CallResult result = CallResult.successNoReturn();

        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
        assertNull(result.getRawValue());
    }

    @Test
    void returnedFalse_shouldReturnReturnedFalseOutcomeWithBoolFalseRawValue() {
        CallResult result = CallResult.returnedFalse();

        assertEquals(CallOutcome.RETURNED_FALSE, result.getOutcome());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", result.getRawValue());
    }

    @Test
    void reverted_shouldReturnRevertedOutcomeWithNullRawValue() {
        CallResult result = CallResult.reverted();

        assertEquals(CallOutcome.REVERTED, result.getOutcome());
        assertNull(result.getRawValue());
    }

    @Test
    void unknown_shouldReturnUnknownOutcomeWithGivenRawHex() {
        String rawHex = "0x02";
        CallResult result = CallResult.unknown(rawHex);

        assertEquals(CallOutcome.UNKNOWN, result.getOutcome());
        assertEquals("0x02", result.getRawValue());
    }

    @Test
    void isSuccess_success_shouldReturnTrue() {
        assertTrue(CallResult.success().isSuccess());
    }

    @Test
    void isSuccess_successNoReturn_shouldReturnTrue() {
        assertTrue(CallResult.successNoReturn().isSuccess());
    }

    @Test
    void isSuccess_returnedFalse_shouldReturnFalse() {
        assertFalse(CallResult.returnedFalse().isSuccess());
    }

    @Test
    void isSuccess_reverted_shouldReturnFalse() {
        assertFalse(CallResult.reverted().isSuccess());
    }

    @Test
    void isSuccess_unknown_shouldReturnFalse() {
        assertFalse(CallResult.unknown("0x02").isSuccess());
    }

    @Test
    void isDangerousFalse_returnedFalse_shouldReturnTrue() {
        assertTrue(CallResult.returnedFalse().isDangerousFalse());
    }

    @Test
    void isDangerousFalse_success_shouldReturnFalse() {
        assertFalse(CallResult.success().isDangerousFalse());
    }

    @Test
    void isDangerousFalse_successNoReturn_shouldReturnFalse() {
        assertFalse(CallResult.successNoReturn().isDangerousFalse());
    }

    @Test
    void isDangerousFalse_reverted_shouldReturnFalse() {
        assertFalse(CallResult.reverted().isDangerousFalse());
    }

    @Test
    void isDangerousFalse_unknown_shouldReturnFalse() {
        assertFalse(CallResult.unknown("0xff").isDangerousFalse());
    }

    @Test
    void immutability_fieldsShouldNotChange() {
        CallResult result = CallResult.success();
        assertEquals(CallOutcome.SUCCESS, result.getOutcome());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001", result.getRawValue());
    }

    @Test
    void toString_shouldContainOutcomeAndRawValue() {
        CallResult result = CallResult.unknown("0x02");
        String str = result.toString();

        assertTrue(str.contains("UNKNOWN"));
        assertTrue(str.contains("0x02"));
    }

    @Test
    void toString_successNoReturn_shouldContainNullRawValue() {
        CallResult result = CallResult.successNoReturn();
        String str = result.toString();

        assertTrue(str.contains("SUCCESS_NO_RETURN"));
    }
}
