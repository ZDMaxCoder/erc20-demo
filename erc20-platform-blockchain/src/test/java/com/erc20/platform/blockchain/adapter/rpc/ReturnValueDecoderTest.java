package com.erc20.platform.blockchain.adapter.rpc;

import com.erc20.platform.blockchain.adapter.model.CallOutcome;
import com.erc20.platform.blockchain.adapter.model.CallResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReturnValueDecoderTest {

    private final ReturnValueDecoder decoder = new ReturnValueDecoder();

    @Test
    void decodeBoolReturn_standardTrueWithPrefix_shouldReturnSuccess() {
        CallResult result = decoder.decodeBoolReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000001");

        assertEquals(CallOutcome.SUCCESS, result.getOutcome());
        assertTrue(result.isSuccess());
    }

    @Test
    void decodeBoolReturn_standardTrueWithoutPrefix_shouldReturnSuccess() {
        CallResult result = decoder.decodeBoolReturn(
                "0000000000000000000000000000000000000000000000000000000000000001");

        assertEquals(CallOutcome.SUCCESS, result.getOutcome());
        assertTrue(result.isSuccess());
    }

    @Test
    void decodeBoolReturn_null_shouldReturnSuccessNoReturn() {
        CallResult result = decoder.decodeBoolReturn(null);

        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
        assertTrue(result.isSuccess());
    }

    @Test
    void decodeBoolReturn_emptyHex0x_shouldReturnSuccessNoReturn() {
        CallResult result = decoder.decodeBoolReturn("0x");

        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
    }

    @Test
    void decodeBoolReturn_emptyString_shouldReturnSuccessNoReturn() {
        CallResult result = decoder.decodeBoolReturn("");

        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
    }

    @Test
    void decodeBoolReturn_allZeros_shouldReturnReturnedFalse() {
        CallResult result = decoder.decodeBoolReturn(
                "0x0000000000000000000000000000000000000000000000000000000000000000");

        assertEquals(CallOutcome.RETURNED_FALSE, result.getOutcome());
        assertTrue(result.isDangerousFalse());
        assertFalse(result.isSuccess());
    }

    @Test
    void decodeBoolReturn_shortData_shouldReturnSuccessNoReturn() {
        CallResult result = decoder.decodeBoolReturn("0x01");

        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
    }

    @Test
    void decodeBoolReturn_shortDataFF_shouldReturnSuccessNoReturn() {
        CallResult result = decoder.decodeBoolReturn("0xff");

        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
    }

    @Test
    void decodeBoolReturn_nonStandardValue02_shouldReturnUnknown() {
        String hex = "0x0000000000000000000000000000000000000000000000000000000000000002";
        CallResult result = decoder.decodeBoolReturn(hex);

        assertEquals(CallOutcome.UNKNOWN, result.getOutcome());
        assertEquals(hex, result.getRawValue());
        assertFalse(result.isSuccess());
        assertFalse(result.isDangerousFalse());
    }

    @Test
    void decodeBoolReturn_largeNonStandardValue_shouldReturnUnknown() {
        String hex = "0x000000000000000000000000000000000000000000000000000000000000ffff";
        CallResult result = decoder.decodeBoolReturn(hex);

        assertEquals(CallOutcome.UNKNOWN, result.getOutcome());
        assertEquals(hex, result.getRawValue());
        assertFalse(result.isSuccess());
        assertFalse(result.isDangerousFalse());
    }
}
