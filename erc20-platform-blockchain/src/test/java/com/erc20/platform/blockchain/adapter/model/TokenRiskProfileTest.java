package com.erc20.platform.blockchain.adapter.model;

import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenRiskProfileTest {

    @Test
    void builder_allFieldsSet_fieldsAccessible() {
        LocalDateTime auditTime = LocalDateTime.of(2026, 5, 16, 10, 0);
        Set<TokenCapability> capabilities = EnumSet.of(
                TokenCapability.STANDARD_RETURN, TokenCapability.PAUSABLE);

        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xAbC123")
                .capabilities(capabilities)
                .riskLevel(RiskLevel.MEDIUM)
                .admissionPassed(true)
                .lastAuditTime(auditTime)
                .autoProcessingAllowed(true)
                .build();

        assertEquals("0xabc123", profile.getContractAddress());
        assertEquals(EnumSet.of(TokenCapability.STANDARD_RETURN, TokenCapability.PAUSABLE),
                profile.getCapabilities());
        assertEquals(RiskLevel.MEDIUM, profile.getRiskLevel());
        assertTrue(profile.isAdmissionPassed());
        assertEquals(auditTime, profile.getLastAuditTime());
        assertTrue(profile.isAutoProcessingAllowed());
    }

    @Test
    void builder_nullLastAuditTime_allowed() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xdef")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(false)
                .lastAuditTime(null)
                .autoProcessingAllowed(false)
                .build();

        assertNull(profile.getLastAuditTime());
        assertFalse(profile.isAdmissionPassed());
        assertFalse(profile.isAutoProcessingAllowed());
    }

    @Test
    void builder_contractAddressLowercased() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xABCDEF")
                .capabilities(Collections.<TokenCapability>emptySet())
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertEquals("0xabcdef", profile.getContractAddress());
    }

    @Test
    void capabilities_returnsUnmodifiableSet() {
        Set<TokenCapability> mutable = EnumSet.of(TokenCapability.PAUSABLE);
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xaaa")
                .capabilities(mutable)
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> profile.getCapabilities().add(TokenCapability.MINTABLE));
    }

    @Test
    void capabilities_defensiveCopy_originalMutationDoesNotAffect() {
        Set<TokenCapability> mutable = EnumSet.of(TokenCapability.PAUSABLE);
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xaaa")
                .capabilities(mutable)
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        mutable.add(TokenCapability.MINTABLE);

        assertFalse(profile.getCapabilities().contains(TokenCapability.MINTABLE));
        assertEquals(1, profile.getCapabilities().size());
    }

    @Test
    void toString_containsKeyFields() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.FEE_ON_TRANSFER))
                .riskLevel(RiskLevel.HIGH)
                .admissionPassed(true)
                .autoProcessingAllowed(false)
                .build();

        String str = profile.toString();
        assertTrue(str.contains("0xabc"));
        assertTrue(str.contains("HIGH"));
        assertTrue(str.contains("FEE_ON_TRANSFER"));
    }

    @Test
    void requiresBalanceDiff_feeOnTransferCapability_returnsTrue() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.FEE_ON_TRANSFER))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertTrue(profile.requiresBalanceDiff());
    }

    @Test
    void requiresBalanceDiff_highRiskWithoutFeeOnTransfer_returnsTrue() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.HIGH)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertTrue(profile.requiresBalanceDiff());
    }

    @Test
    void requiresBalanceDiff_lowRiskStandardToken_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertFalse(profile.requiresBalanceDiff());
    }

    @Test
    void requiresApproveReset_approveRaceConditionCapability_returnsTrue() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.APPROVE_RACE_CONDITION))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertTrue(profile.requiresApproveReset());
    }

    @Test
    void requiresApproveReset_noApproveRaceCondition_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertFalse(profile.requiresApproveReset());
    }

    @Test
    void isStandardProcessing_lowRiskNoFeeNoRebasing_returnsTrue() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertTrue(profile.isStandardProcessing());
    }

    @Test
    void isStandardProcessing_feeOnTransfer_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.FEE_ON_TRANSFER))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertFalse(profile.isStandardProcessing());
    }

    @Test
    void isStandardProcessing_rebasingCapability_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.REBASING))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertFalse(profile.isStandardProcessing());
    }

    @Test
    void isStandardProcessing_highRisk_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.HIGH)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertFalse(profile.isStandardProcessing());
    }

    @Test
    void builder_emptyCapabilities_allowed() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0x123")
                .capabilities(Collections.<TokenCapability>emptySet())
                .riskLevel(RiskLevel.CRITICAL)
                .admissionPassed(false)
                .autoProcessingAllowed(false)
                .build();

        assertTrue(profile.getCapabilities().isEmpty());
        assertEquals(RiskLevel.CRITICAL, profile.getRiskLevel());
    }

    @Test
    void builder_circuitBreakerOpenTrue_returnsTrue() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .circuitBreakerOpen(true)
                .build();

        assertTrue(profile.isCircuitBreakerOpen());
    }

    @Test
    void builder_circuitBreakerOpenFalse_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .circuitBreakerOpen(false)
                .build();

        assertFalse(profile.isCircuitBreakerOpen());
    }

    @Test
    void builder_circuitBreakerOpenDefault_returnsFalse() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .build();

        assertFalse(profile.isCircuitBreakerOpen());
    }

    @Test
    void toString_containsCircuitBreakerOpen() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress("0xabc")
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .autoProcessingAllowed(true)
                .circuitBreakerOpen(true)
                .build();

        String str = profile.toString();
        assertTrue(str.contains("circuitBreakerOpen=true"));
    }
}
