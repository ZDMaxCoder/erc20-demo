package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenPausedExceptionTest {

    @Test
    void constructor_withContractAddress_shouldCarryField() {
        TokenPausedException ex = new TokenPausedException("0xabc");

        assertEquals("0xabc", ex.getContractAddress());
    }

    @Test
    void constructor_shouldLowercaseContractAddress() {
        TokenPausedException ex = new TokenPausedException("0xABC");

        assertEquals("0xabc", ex.getContractAddress());
    }

    @Test
    void getMessage_shouldContainContractAddress() {
        TokenPausedException ex = new TokenPausedException("0xabc");

        assertTrue(ex.getMessage().contains("0xabc"));
    }

    @Test
    void inheritance_shouldExtendERC20AdapterException() {
        TokenPausedException ex = new TokenPausedException("0xabc");

        assertInstanceOf(ERC20AdapterException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(TokenPausedException.class.getDeclaredField("serialVersionUID"));
    }

    @Test
    void toString_shouldContainMeaningfulInfo() {
        TokenPausedException ex = new TokenPausedException("0xabc");

        String str = ex.toString();
        assertTrue(str.contains("0xabc"));
    }
}
