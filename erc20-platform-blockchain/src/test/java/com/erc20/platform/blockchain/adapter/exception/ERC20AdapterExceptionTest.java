package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ERC20AdapterExceptionTest {

    @Test
    void constructor_withMessageAndCause_shouldCarryBoth() {
        IOException cause = new IOException("network error");
        ERC20AdapterException ex = new ERC20AdapterException("balanceOf failed", cause);

        assertEquals("balanceOf failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void constructor_withMessageOnly_shouldCarryMessage() {
        ERC20AdapterException ex = new ERC20AdapterException("transfer rejected");

        assertEquals("transfer rejected", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void inheritance_shouldBeRuntimeException() {
        ERC20AdapterException ex = new ERC20AdapterException("test");

        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(ERC20AdapterException.class.getDeclaredField("serialVersionUID"));
    }
}
