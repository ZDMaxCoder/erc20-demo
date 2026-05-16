package com.erc20.platform.common.util;

import com.erc20.platform.common.exception.AmountOverflowException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class AmountUtil {

    private AmountUtil() {
    }

    public static long toMinUnit(BigDecimal humanReadable, int exponent) {
        return humanReadable.movePointRight(exponent).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal toHumanReadable(long minUnit, int exponent) {
        return BigDecimal.valueOf(minUnit).movePointLeft(exponent);
    }

    public static BigInteger toChainAmount(long minUnit, int amountExponent, int tokenDecimals) {
        BigInteger base = BigInteger.valueOf(minUnit);
        int diff = tokenDecimals - amountExponent;
        BigInteger result;
        if (diff > 0) {
            result = base.multiply(BigInteger.TEN.pow(diff));
        } else if (diff < 0) {
            result = base.divide(BigInteger.TEN.pow(-diff));
        } else {
            result = base;
        }
        checkOverflow(result);
        return result;
    }

    public static long fromChainAmount(BigInteger chainAmount, int tokenDecimals, int amountExponent) {
        int diff = tokenDecimals - amountExponent;
        BigInteger result;
        if (diff > 0) {
            result = chainAmount.divide(BigInteger.TEN.pow(diff));
        } else if (diff < 0) {
            result = chainAmount.multiply(BigInteger.TEN.pow(-diff));
        } else {
            result = chainAmount;
        }
        checkOverflow(result);
        return result.longValueExact();
    }

    private static void checkOverflow(BigInteger value) {
        if (value.bitLength() > 63) {
            throw new AmountOverflowException(value.toString());
        }
    }
}
