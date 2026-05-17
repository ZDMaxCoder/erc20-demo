package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.exception.TokenAdmissionRejectedException;
import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class TokenAdmissionGatewayTest {

    private TokenRiskProfileRegistry registry;
    private TokenAdmissionGateway gateway;

    private static final String USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String UNKNOWN_TOKEN = "0x0000000000000000000000000000000000000001";
    private static final String CRITICAL_TOKEN = "0x0000000000000000000000000000000000000002";
    private static final String REBASING_TOKEN = "0x0000000000000000000000000000000000000003";
    private static final String FEE_TOKEN = "0x0000000000000000000000000000000000000004";
    private static final String BREAKER_OPEN_TOKEN = "0x0000000000000000000000000000000000000005";

    @BeforeEach
    void setUp() {
        registry = Mockito.mock(TokenRiskProfileRegistry.class);
        gateway = new TokenAdmissionGateway(registry);
    }

    @Test
    void checkAdmission_standardAdmittedToken_noException() {
        when(registry.getProfile(USDT_CONTRACT)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(USDT_CONTRACT)
                        .capabilities(EnumSet.of(TokenCapability.NO_RETURN_VALUE))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(true)
                        .autoProcessingAllowed(true)
                        .build()
        );

        assertDoesNotThrow(() -> gateway.checkAdmission(USDT_CONTRACT, "transfer"));
    }

    @Test
    void checkAdmission_notAdmittedToken_throwsRejected() {
        when(registry.getProfile(UNKNOWN_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(UNKNOWN_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(false)
                        .autoProcessingAllowed(true)
                        .build()
        );

        TokenAdmissionRejectedException ex = assertThrows(
                TokenAdmissionRejectedException.class,
                () -> gateway.checkAdmission(UNKNOWN_TOKEN, "transfer")
        );
        assertTrue(ex.getMessage().contains("has not passed admission review"));
        assertEquals(UNKNOWN_TOKEN, ex.getContractAddress());
    }

    @Test
    void checkAdmission_criticalRiskLevel_throwsRejected() {
        when(registry.getProfile(CRITICAL_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(CRITICAL_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                        .riskLevel(RiskLevel.CRITICAL)
                        .admissionPassed(true)
                        .autoProcessingAllowed(false)
                        .build()
        );

        TokenAdmissionRejectedException ex = assertThrows(
                TokenAdmissionRejectedException.class,
                () -> gateway.checkAdmission(CRITICAL_TOKEN, "transfer")
        );
        assertTrue(ex.getMessage().contains("CRITICAL"));
    }

    @Test
    void checkAdmission_rebasingTokenNotAutoProcessing_throwsRejected() {
        when(registry.getProfile(REBASING_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(REBASING_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.REBASING))
                        .riskLevel(RiskLevel.HIGH)
                        .admissionPassed(true)
                        .autoProcessingAllowed(false)
                        .build()
        );

        TokenAdmissionRejectedException ex = assertThrows(
                TokenAdmissionRejectedException.class,
                () -> gateway.checkAdmission(REBASING_TOKEN, "transfer")
        );
        assertTrue(ex.getMessage().contains("REBASING"));
    }

    @Test
    void checkAdmission_feeOnTransferWithAutoProcessing_noException() {
        when(registry.getProfile(FEE_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(FEE_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.FEE_ON_TRANSFER))
                        .riskLevel(RiskLevel.MEDIUM)
                        .admissionPassed(true)
                        .autoProcessingAllowed(true)
                        .build()
        );

        assertDoesNotThrow(() -> gateway.checkAdmission(FEE_TOKEN, "transfer"));
    }

    @Test
    void isAdmitted_admittedToken_returnsTrue() {
        when(registry.getProfile(USDT_CONTRACT)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(USDT_CONTRACT)
                        .capabilities(EnumSet.of(TokenCapability.NO_RETURN_VALUE))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(true)
                        .autoProcessingAllowed(true)
                        .build()
        );

        assertTrue(gateway.isAdmitted(USDT_CONTRACT));
    }

    @Test
    void isAdmitted_rejectedToken_returnsFalse() {
        when(registry.getProfile(UNKNOWN_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(UNKNOWN_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(false)
                        .autoProcessingAllowed(true)
                        .build()
        );

        assertFalse(gateway.isAdmitted(UNKNOWN_TOKEN));
    }

    @Test
    void isAdmitted_criticalRiskLevel_returnsFalse() {
        when(registry.getProfile(CRITICAL_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(CRITICAL_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                        .riskLevel(RiskLevel.CRITICAL)
                        .admissionPassed(true)
                        .autoProcessingAllowed(false)
                        .build()
        );

        assertFalse(gateway.isAdmitted(CRITICAL_TOKEN));
    }

    @Test
    void checkAdmission_circuitBreakerOpen_throwsRejected() {
        when(registry.getProfile(BREAKER_OPEN_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(BREAKER_OPEN_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(true)
                        .autoProcessingAllowed(true)
                        .circuitBreakerOpen(true)
                        .build()
        );

        TokenAdmissionRejectedException ex = assertThrows(
                TokenAdmissionRejectedException.class,
                () -> gateway.checkAdmission(BREAKER_OPEN_TOKEN, "transfer")
        );
        assertTrue(ex.getMessage().contains("circuit breaker is OPEN"));
    }

    @Test
    void checkAdmission_circuitBreakerClosed_noException() {
        when(registry.getProfile(USDT_CONTRACT)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(USDT_CONTRACT)
                        .capabilities(EnumSet.of(TokenCapability.NO_RETURN_VALUE))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(true)
                        .autoProcessingAllowed(true)
                        .circuitBreakerOpen(false)
                        .build()
        );

        assertDoesNotThrow(() -> gateway.checkAdmission(USDT_CONTRACT, "transfer"));
    }

    @Test
    void isAdmitted_circuitBreakerOpen_returnsFalse() {
        when(registry.getProfile(BREAKER_OPEN_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(BREAKER_OPEN_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                        .riskLevel(RiskLevel.LOW)
                        .admissionPassed(true)
                        .autoProcessingAllowed(true)
                        .circuitBreakerOpen(true)
                        .build()
        );

        assertFalse(gateway.isAdmitted(BREAKER_OPEN_TOKEN));
    }

    @Test
    void checkAdmission_feeOnTransferWithoutAutoProcessing_throwsRejected() {
        when(registry.getProfile(FEE_TOKEN)).thenReturn(
                TokenRiskProfile.builder()
                        .contractAddress(FEE_TOKEN)
                        .capabilities(EnumSet.of(TokenCapability.FEE_ON_TRANSFER))
                        .riskLevel(RiskLevel.HIGH)
                        .admissionPassed(true)
                        .autoProcessingAllowed(false)
                        .build()
        );

        TokenAdmissionRejectedException ex = assertThrows(
                TokenAdmissionRejectedException.class,
                () -> gateway.checkAdmission(FEE_TOKEN, "transfer")
        );
        assertTrue(ex.getMessage().contains("FEE_ON_TRANSFER"));
    }
}
