package com.erc20.platform.blockchain.adapter.exception;

import java.math.BigInteger;

public final class AmountMismatchException extends ERC20AdapterException {

    private static final long serialVersionUID = 1L;

    private final BigInteger expectedAmount;
    private final BigInteger actualAmount;

    public AmountMismatchException(BigInteger expectedAmount, BigInteger actualAmount) {
        super("Amount mismatch: expected " + expectedAmount + " but got " + actualAmount);
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
    }

    public BigInteger getExpectedAmount() {
        return expectedAmount;
    }

    public BigInteger getActualAmount() {
        return actualAmount;
    }

    @Override
    public String toString() {
        return "AmountMismatchException{expectedAmount=" + expectedAmount
                + ", actualAmount=" + actualAmount + "}";
    }
}
