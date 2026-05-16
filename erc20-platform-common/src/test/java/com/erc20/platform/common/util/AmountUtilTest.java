package com.erc20.platform.common.util;

import com.erc20.platform.common.exception.AmountOverflowException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class AmountUtilTest {

    @Test
    void toMinUnit_normalAmount() {
        assertEquals(1234L, AmountUtil.toMinUnit(new BigDecimal("12.34"), 2));
    }

    @Test
    void toMinUnit_zero() {
        assertEquals(0L, AmountUtil.toMinUnit(BigDecimal.ZERO, 2));
    }

    @Test
    void toMinUnit_wholeNumber() {
        assertEquals(1200L, AmountUtil.toMinUnit(new BigDecimal("12"), 2));
    }

    @Test
    void toMinUnit_sixDecimals() {
        assertEquals(1000000L, AmountUtil.toMinUnit(new BigDecimal("1.000000"), 6));
    }

    @Test
    void toHumanReadable_normalAmount() {
        assertEquals(0, new BigDecimal("12.34").compareTo(AmountUtil.toHumanReadable(1234L, 2)));
    }

    @Test
    void toHumanReadable_zero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(AmountUtil.toHumanReadable(0L, 2)));
    }

    @Test
    void toChainAmount_sameExponentAndDecimals() {
        assertEquals(new BigInteger("100000000"), AmountUtil.toChainAmount(100000000L, 6, 6));
    }

    @Test
    void toChainAmount_differentExponentAndDecimals() {
        // minUnit=1234 with exponent=2 means 12.34, on chain with 6 decimals = 12340000
        assertEquals(new BigInteger("12340000"), AmountUtil.toChainAmount(1234L, 2, 6));
    }

    @Test
    void fromChainAmount_sameExponentAndDecimals() {
        assertEquals(100000000L, AmountUtil.fromChainAmount(new BigInteger("100000000"), 6, 6));
    }

    @Test
    void fromChainAmount_differentExponentAndDecimals() {
        // chainAmount=12340000 with 6 decimals means 12.34, stored with exponent=2 = 1234
        assertEquals(1234L, AmountUtil.fromChainAmount(new BigInteger("12340000"), 6, 2));
    }

    @Test
    void toMinUnit_largeAmountNoOverflow() {
        // Use a large but safe value
        long result = AmountUtil.toMinUnit(new BigDecimal("9000000000000"), 2);
        assertEquals(900000000000000L, result);
    }

    @Test
    void roundTrip_preservesPrecision() {
        long original = 123456789L;
        int exponent = 4;
        BigDecimal human = AmountUtil.toHumanReadable(original, exponent);
        long backToMinUnit = AmountUtil.toMinUnit(human, exponent);
        assertEquals(original, backToMinUnit);
    }

    @Test
    void chainRoundTrip_preservesPrecision() {
        long minUnit = 5000L;
        int amountExponent = 6;
        int tokenDecimals = 18;
        BigInteger chain = AmountUtil.toChainAmount(minUnit, amountExponent, tokenDecimals);
        long back = AmountUtil.fromChainAmount(chain, tokenDecimals, amountExponent);
        assertEquals(minUnit, back);
    }

    @Test
    void fromChainAmount_overflow_throwsAmountOverflowException() {
        BigInteger hugeAmount = BigInteger.TEN.pow(32);
        assertThrows(AmountOverflowException.class,
                () -> AmountUtil.fromChainAmount(hugeAmount, 18, 6));
    }

    @Test
    void fromChainAmount_exactlyMaxLong_succeeds() {
        BigInteger maxLongChain = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, AmountUtil.fromChainAmount(maxLongChain, 6, 6));
    }

    @Test
    void fromChainAmount_justAboveMaxLong_throwsAmountOverflowException() {
        BigInteger aboveMax = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThrows(AmountOverflowException.class,
                () -> AmountUtil.fromChainAmount(aboveMax, 6, 6));
    }

    @Test
    void fromChainAmount_multiplyOverflow_throwsAmountOverflowException() {
        BigInteger amount = BigInteger.valueOf(Long.MAX_VALUE);
        assertThrows(AmountOverflowException.class,
                () -> AmountUtil.fromChainAmount(amount, 2, 6));
    }

    @Test
    void toChainAmount_normalConversion_succeeds() {
        BigInteger result = AmountUtil.toChainAmount(1000L, 6, 18);
        assertEquals(new BigInteger("1000000000000000"), result);
    }

    @Test
    void toChainAmount_resultExceeds63Bits_throwsAmountOverflowException() {
        assertThrows(AmountOverflowException.class,
                () -> AmountUtil.toChainAmount(Long.MAX_VALUE, 6, 30));
    }
}
