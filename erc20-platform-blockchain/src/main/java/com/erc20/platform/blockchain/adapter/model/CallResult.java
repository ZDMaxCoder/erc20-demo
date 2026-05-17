package com.erc20.platform.blockchain.adapter.model;

public final class CallResult {

    private static final String BOOL_TRUE_HEX = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private static final String BOOL_FALSE_HEX = "0x0000000000000000000000000000000000000000000000000000000000000000";

    private final CallOutcome outcome;
    private final String rawValue;

    private CallResult(CallOutcome outcome, String rawValue) {
        this.outcome = outcome;
        this.rawValue = rawValue;
    }

    public static CallResult success() {
        return new CallResult(CallOutcome.SUCCESS, BOOL_TRUE_HEX);
    }

    public static CallResult successNoReturn() {
        return new CallResult(CallOutcome.SUCCESS_NO_RETURN, null);
    }

    public static CallResult returnedFalse() {
        return new CallResult(CallOutcome.RETURNED_FALSE, BOOL_FALSE_HEX);
    }

    public static CallResult reverted() {
        return new CallResult(CallOutcome.REVERTED, null);
    }

    public static CallResult unknown(String rawHex) {
        return new CallResult(CallOutcome.UNKNOWN, rawHex);
    }

    public boolean isSuccess() {
        return outcome == CallOutcome.SUCCESS || outcome == CallOutcome.SUCCESS_NO_RETURN;
    }

    public boolean isDangerousFalse() {
        return outcome == CallOutcome.RETURNED_FALSE;
    }

    public CallOutcome getOutcome() {
        return outcome;
    }

    public String getRawValue() {
        return rawValue;
    }

    @Override
    public String toString() {
        return "CallResult{outcome=" + outcome + ", rawValue=" + rawValue + "}";
    }
}
