package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferRevertedExceptionTest {

    @Test
    void constructor_withContractAndReason_shouldCarryFields() {
        TransferRevertedException ex = new TransferRevertedException("0xabc", "Pausable: paused");

        assertEquals("0xabc", ex.getContractAddress());
        assertEquals("Pausable: paused", ex.getRevertReason());
    }

    @Test
    void constructor_shouldLowercaseContractAddress() {
        TransferRevertedException ex = new TransferRevertedException("0xABC", "reverted");

        assertEquals("0xabc", ex.getContractAddress());
    }

    @Test
    void getMessage_shouldContainContractAndReason() {
        TransferRevertedException ex = new TransferRevertedException("0xabc", "Pausable: paused");

        assertTrue(ex.getMessage().contains("0xabc"));
        assertTrue(ex.getMessage().contains("Pausable: paused"));
    }

    @Test
    void inheritance_shouldExtendERC20AdapterException() {
        TransferRevertedException ex = new TransferRevertedException("0xabc", "reverted");

        assertInstanceOf(ERC20AdapterException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(TransferRevertedException.class.getDeclaredField("serialVersionUID"));
    }

    @Test
    void toString_shouldContainMeaningfulInfo() {
        TransferRevertedException ex = new TransferRevertedException("0xabc", "Pausable: paused");

        String str = ex.toString();
        assertTrue(str.contains("0xabc"));
        assertTrue(str.contains("Pausable: paused"));
    }
}
