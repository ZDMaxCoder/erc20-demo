package com.erc20.platform.blockchain.adapter.exception;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class AmountMismatchExceptionTest {

    @Test
    void constructor_withExpectedAndActual_shouldCarryFields() {
        AmountMismatchException ex = new AmountMismatchException(
                BigInteger.valueOf(1000), BigInteger.valueOf(990));

        assertEquals(BigInteger.valueOf(1000), ex.getExpectedAmount());
        assertEquals(BigInteger.valueOf(990), ex.getActualAmount());
    }

    @Test
    void getMessage_shouldContainAmounts() {
        AmountMismatchException ex = new AmountMismatchException(
                BigInteger.valueOf(1000), BigInteger.valueOf(990));

        assertTrue(ex.getMessage().contains("1000"));
        assertTrue(ex.getMessage().contains("990"));
    }

    @Test
    void inheritance_shouldExtendERC20AdapterException() {
        AmountMismatchException ex = new AmountMismatchException(
                BigInteger.valueOf(1000), BigInteger.valueOf(990));

        assertInstanceOf(ERC20AdapterException.class, ex);
    }

    @Test
    void serialVersionUID_shouldBeDeclared() throws NoSuchFieldException {
        assertNotNull(AmountMismatchException.class.getDeclaredField("serialVersionUID"));
    }

    @Test
    void toString_shouldContainMeaningfulInfo() {
        AmountMismatchException ex = new AmountMismatchException(
                BigInteger.valueOf(1000), BigInteger.valueOf(990));

        String str = ex.toString();
        assertTrue(str.contains("1000"));
        assertTrue(str.contains("990"));
    }
}
