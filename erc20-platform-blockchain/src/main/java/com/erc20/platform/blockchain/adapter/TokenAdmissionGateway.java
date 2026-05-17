package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.exception.TokenAdmissionRejectedException;
import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import org.springframework.stereotype.Component;

@Component
public class TokenAdmissionGateway {

    private final TokenRiskProfileRegistry registry;

    public TokenAdmissionGateway(TokenRiskProfileRegistry registry) {
        this.registry = registry;
    }

    public void checkAdmission(String contract, String operation) {
        TokenRiskProfile profile = registry.getProfile(contract);

        if (!profile.isAdmissionPassed()) {
            throw new TokenAdmissionRejectedException(contract,
                    "Token has not passed admission review for operation: " + operation);
        }

        if (profile.getRiskLevel() == RiskLevel.CRITICAL) {
            throw new TokenAdmissionRejectedException(contract,
                    "Token risk level is CRITICAL, operation " + operation + " is not allowed");
        }

        if (profile.getCapabilities().contains(TokenCapability.REBASING)
                && !profile.isAutoProcessingAllowed()) {
            throw new TokenAdmissionRejectedException(contract,
                    "REBASING token requires manual processing for operation: " + operation);
        }

        if (profile.getCapabilities().contains(TokenCapability.FEE_ON_TRANSFER)
                && !profile.isAutoProcessingAllowed()) {
            throw new TokenAdmissionRejectedException(contract,
                    "FEE_ON_TRANSFER token requires manual processing for operation: " + operation);
        }

        if (profile.isCircuitBreakerOpen()) {
            throw new TokenAdmissionRejectedException(contract,
                    "circuit breaker is OPEN for operation: " + operation);
        }
    }

    public boolean isAdmitted(String contract) {
        try {
            checkAdmission(contract, "query");
            return true;
        } catch (TokenAdmissionRejectedException e) {
            return false;
        }
    }
}
