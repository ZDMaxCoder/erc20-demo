package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBlacklistedExceptionTest {

    @Test
    void constructor_withContractAndAddress_shouldCarryFields() {
        TokenBlacklistedException ex = new TokenBlacklistedException("0xabc", "0xdef");

        assertEquals("0xabc", ex.getContractAddress());
        assertEquals("0xdef", ex.getAddress());
    }

    @Test
    void constructor_shouldLowercaseAllAddresses() {
        TokenBlacklistedException ex = new TokenBlacklistedException("0xABC", "0xDEF");

        assertEquals("0xabc", ex.getContractAddress());
        assertEquals("0xdef", ex.getAddress());
    }

    @Test
    void getMessage_shouldContainContractAndAddress() {
        TokenBlacklistedException ex = new TokenBlacklistedException("0xabc", "0xdef");

        assertTrue(ex.getMessage().contains("0xabc"));
        assertTrue(ex.getMessage().contains("0xdef"));
    }

    @Test
    void inheritance_shouldExtendERC20AdapterException() {
        TokenBlacklistedException ex = new TokenBlacklistedException("0xabc", "0xdef");

        assertInstanceOf(ERC20AdapterException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(TokenBlacklistedException.class.getDeclaredField("serialVersionUID"));
    }

    @Test
    void toString_shouldContainMeaningfulInfo() {
        TokenBlacklistedException ex = new TokenBlacklistedException("0xabc", "0xdef");

        String str = ex.toString();
        assertTrue(str.contains("0xabc"));
        assertTrue(str.contains("0xdef"));
    }
}
