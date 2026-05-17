package com.erc20.platform.blockchain.adapter.model;

import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class TokenRiskProfile {

    private final String contractAddress;
    private final Set<TokenCapability> capabilities;
    private final RiskLevel riskLevel;
    private final boolean admissionPassed;
    private final LocalDateTime lastAuditTime;
    private final boolean autoProcessingAllowed;
    private final boolean circuitBreakerOpen;

    private TokenRiskProfile(Builder builder) {
        this.contractAddress = builder.contractAddress == null
                ? null : builder.contractAddress.toLowerCase();
        this.capabilities = builder.capabilities == null || builder.capabilities.isEmpty()
                ? Collections.<TokenCapability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.capabilities));
        this.riskLevel = builder.riskLevel;
        this.admissionPassed = builder.admissionPassed;
        this.lastAuditTime = builder.lastAuditTime;
        this.autoProcessingAllowed = builder.autoProcessingAllowed;
        this.circuitBreakerOpen = builder.circuitBreakerOpen;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public Set<TokenCapability> getCapabilities() {
        return capabilities;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public boolean isAdmissionPassed() {
        return admissionPassed;
    }

    public LocalDateTime getLastAuditTime() {
        return lastAuditTime;
    }

    public boolean isAutoProcessingAllowed() {
        return autoProcessingAllowed;
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreakerOpen;
    }

    public boolean requiresBalanceDiff() {
        return capabilities.contains(TokenCapability.FEE_ON_TRANSFER)
                || riskLevel.compareTo(RiskLevel.HIGH) >= 0;
    }

    public boolean requiresApproveReset() {
        return capabilities.contains(TokenCapability.APPROVE_RACE_CONDITION);
    }

    public boolean isStandardProcessing() {
        return riskLevel == RiskLevel.LOW
                && !capabilities.contains(TokenCapability.FEE_ON_TRANSFER)
                && !capabilities.contains(TokenCapability.REBASING);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "TokenRiskProfile{contractAddress=" + contractAddress
                + ", capabilities=" + capabilities
                + ", riskLevel=" + riskLevel
                + ", admissionPassed=" + admissionPassed
                + ", autoProcessingAllowed=" + autoProcessingAllowed
                + ", circuitBreakerOpen=" + circuitBreakerOpen
                + "}";
    }

    public static final class Builder {
        private String contractAddress;
        private Set<TokenCapability> capabilities;
        private RiskLevel riskLevel;
        private boolean admissionPassed;
        private LocalDateTime lastAuditTime;
        private boolean autoProcessingAllowed;
        private boolean circuitBreakerOpen;

        private Builder() {
        }

        public Builder contractAddress(String contractAddress) {
            this.contractAddress = contractAddress;
            return this;
        }

        public Builder capabilities(Set<TokenCapability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder admissionPassed(boolean admissionPassed) {
            this.admissionPassed = admissionPassed;
            return this;
        }

        public Builder lastAuditTime(LocalDateTime lastAuditTime) {
            this.lastAuditTime = lastAuditTime;
            return this;
        }

        public Builder autoProcessingAllowed(boolean autoProcessingAllowed) {
            this.autoProcessingAllowed = autoProcessingAllowed;
            return this;
        }

        public Builder circuitBreakerOpen(boolean circuitBreakerOpen) {
            this.circuitBreakerOpen = circuitBreakerOpen;
            return this;
        }

        public TokenRiskProfile build() {
            return new TokenRiskProfile(this);
        }
    }
}
