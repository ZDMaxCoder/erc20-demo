package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenAdmissionRejectedExceptionTest {

    @Test
    void constructor_withContractAndReason_shouldCarryFields() {
        TokenAdmissionRejectedException ex = new TokenAdmissionRejectedException("0xabc", "REBASING token not allowed");

        assertEquals("0xabc", ex.getContractAddress());
        assertEquals("REBASING token not allowed", ex.getReason());
    }

    @Test
    void constructor_shouldLowercaseContractAddress() {
        TokenAdmissionRejectedException ex = new TokenAdmissionRejectedException("0xABC", "rejected");

        assertEquals("0xabc", ex.getContractAddress());
    }

    @Test
    void getMessage_shouldContainContractAndReason() {
        TokenAdmissionRejectedException ex = new TokenAdmissionRejectedException("0xabc", "CRITICAL risk level");

        assertTrue(ex.getMessage().contains("0xabc"));
        assertTrue(ex.getMessage().contains("CRITICAL risk level"));
    }

    @Test
    void inheritance_shouldExtendERC20AdapterException() {
        TokenAdmissionRejectedException ex = new TokenAdmissionRejectedException("0xabc", "rejected");

        assertInstanceOf(ERC20AdapterException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(TokenAdmissionRejectedException.class.getDeclaredField("serialVersionUID"));
    }

    @Test
    void toString_shouldContainMeaningfulInfo() {
        TokenAdmissionRejectedException ex = new TokenAdmissionRejectedException("0xabc", "not admitted");

        String str = ex.toString();
        assertTrue(str.contains("0xabc"));
        assertTrue(str.contains("not admitted"));
    }
}
