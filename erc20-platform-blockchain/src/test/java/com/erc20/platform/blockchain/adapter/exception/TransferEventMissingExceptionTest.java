package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferEventMissingExceptionTest {

    @Test
    void constructor_withTxHash_shouldCarryField() {
        TransferEventMissingException ex = new TransferEventMissingException("0xdef");

        assertEquals("0xdef", ex.getTxHash());
    }

    @Test
    void getMessage_shouldContainTxHash() {
        TransferEventMissingException ex = new TransferEventMissingException("0xdef");

        assertTrue(ex.getMessage().contains("0xdef"));
    }

    @Test
    void inheritance_shouldExtendERC20AdapterException() {
        TransferEventMissingException ex = new TransferEventMissingException("0xdef");

        assertInstanceOf(ERC20AdapterException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(TransferEventMissingException.class.getDeclaredField("serialVersionUID"));
    }

    @Test
    void toString_shouldContainMeaningfulInfo() {
        TransferEventMissingException ex = new TransferEventMissingException("0xdef");

        String str = ex.toString();
        assertTrue(str.contains("0xdef"));
    }
}
