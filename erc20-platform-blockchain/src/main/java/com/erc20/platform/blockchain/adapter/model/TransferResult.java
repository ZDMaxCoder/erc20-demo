package com.erc20.platform.blockchain.adapter.model;

import com.erc20.platform.blockchain.erc20.TransferEvent;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TransferResult {

    private final TransferOutcome outcome;
    private final String txHash;
    private final Long blockNumber;
    private final BigInteger actualAmount;
    private final BigInteger expectedAmount;
    private final BigInteger balanceDiff;
    private final String anomalyReason;
    private final List<TransferEvent> events;

    private TransferResult(Builder builder) {
        this.outcome = builder.outcome;
        this.txHash = builder.txHash;
        this.blockNumber = builder.blockNumber;
        this.actualAmount = builder.actualAmount;
        this.expectedAmount = builder.expectedAmount;
        this.balanceDiff = builder.balanceDiff;
        this.anomalyReason = builder.anomalyReason;
        this.events = builder.events == null
                ? Collections.<TransferEvent>emptyList()
                : Collections.unmodifiableList(new ArrayList<TransferEvent>(builder.events));
    }

    public static TransferResult failed(String txHash, String reason) {
        return new Builder()
                .outcome(TransferOutcome.FAILED)
                .txHash(txHash)
                .anomalyReason(reason)
                .build();
    }

    public static TransferResult pending(String txHash) {
        return new Builder()
                .outcome(TransferOutcome.PENDING)
                .txHash(txHash)
                .build();
    }

    public boolean isAmountConsistent() {
        if (actualAmount == null && expectedAmount == null) {
            return true;
        }
        if (actualAmount == null || expectedAmount == null) {
            return false;
        }
        return actualAmount.compareTo(expectedAmount) == 0;
    }

    public boolean hasBalanceDiffAnomaly() {
        if (balanceDiff == null || actualAmount == null) {
            return false;
        }
        return balanceDiff.compareTo(actualAmount) != 0;
    }

    public TransferOutcome getOutcome() {
        return outcome;
    }

    public String getTxHash() {
        return txHash;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public BigInteger getActualAmount() {
        return actualAmount;
    }

    public BigInteger getExpectedAmount() {
        return expectedAmount;
    }

    public BigInteger getBalanceDiff() {
        return balanceDiff;
    }

    public String getAnomalyReason() {
        return anomalyReason;
    }

    public List<TransferEvent> getEvents() {
        return events;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "TransferResult{outcome=" + outcome
                + ", txHash=" + txHash
                + ", blockNumber=" + blockNumber
                + ", actualAmount=" + actualAmount
                + ", expectedAmount=" + expectedAmount
                + "}";
    }

    public static final class Builder {
        private TransferOutcome outcome;
        private String txHash;
        private Long blockNumber;
        private BigInteger actualAmount;
        private BigInteger expectedAmount;
        private BigInteger balanceDiff;
        private String anomalyReason;
        private List<TransferEvent> events;

        private Builder() {
        }

        public Builder outcome(TransferOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder txHash(String txHash) {
            this.txHash = txHash;
            return this;
        }

        public Builder blockNumber(Long blockNumber) {
            this.blockNumber = blockNumber;
            return this;
        }

        public Builder actualAmount(BigInteger actualAmount) {
            this.actualAmount = actualAmount;
            return this;
        }

        public Builder expectedAmount(BigInteger expectedAmount) {
            this.expectedAmount = expectedAmount;
            return this;
        }

        public Builder balanceDiff(BigInteger balanceDiff) {
            this.balanceDiff = balanceDiff;
            return this;
        }

        public Builder anomalyReason(String anomalyReason) {
            this.anomalyReason = anomalyReason;
            return this;
        }

        public Builder events(List<TransferEvent> events) {
            this.events = events;
            return this;
        }

        public TransferResult build() {
            return new TransferResult(this);
        }
    }
}
