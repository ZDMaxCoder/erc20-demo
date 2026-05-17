package com.erc20.platform.blockchain.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRiskProfileRegistryTest {

    private static final String USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String UNKNOWN_CONTRACT = "0x0000000000000000000000000000000000000000";

    @Mock
    private TokenConfigMapper tokenConfigMapper;

    private TokenRiskProfileRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TokenRiskProfileRegistry(tokenConfigMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_configuredToken_returnsProfileFromDb() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("NO_RETURN_VALUE")
                .riskLevel("LOW")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertNotNull(profile);
        assertEquals(USDT_CONTRACT, profile.getContractAddress());
        assertTrue(profile.getCapabilities().contains(TokenCapability.NO_RETURN_VALUE));
        assertEquals(1, profile.getCapabilities().size());
        assertEquals(RiskLevel.LOW, profile.getRiskLevel());
        assertTrue(profile.isAdmissionPassed());
        assertTrue(profile.isAutoProcessingAllowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_multipleCapabilities_parsesAll() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("NO_RETURN_VALUE,APPROVE_RACE_CONDITION,PAUSABLE")
                .riskLevel("MEDIUM")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertEquals(3, profile.getCapabilities().size());
        assertTrue(profile.getCapabilities().contains(TokenCapability.NO_RETURN_VALUE));
        assertTrue(profile.getCapabilities().contains(TokenCapability.APPROVE_RACE_CONDITION));
        assertTrue(profile.getCapabilities().contains(TokenCapability.PAUSABLE));
        assertEquals(RiskLevel.MEDIUM, profile.getRiskLevel());
        assertTrue(profile.isAutoProcessingAllowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_highRiskToken_autoProcessingDisabled() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("FEE_ON_TRANSFER")
                .riskLevel("HIGH")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertEquals(RiskLevel.HIGH, profile.getRiskLevel());
        assertFalse(profile.isAutoProcessingAllowed());
        assertTrue(profile.isAdmissionPassed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_nullCapabilities_treatedAsStandardReturn() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities(null)
                .riskLevel(null)
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertTrue(profile.getCapabilities().contains(TokenCapability.STANDARD_RETURN));
        assertEquals(1, profile.getCapabilities().size());
        assertEquals(RiskLevel.LOW, profile.getRiskLevel());
        assertTrue(profile.isAdmissionPassed());
        assertTrue(profile.isAutoProcessingAllowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_disabledToken_admissionNotPassed() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("STANDARD_RETURN")
                .riskLevel("LOW")
                .enabled(0)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertFalse(profile.isAdmissionPassed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_unknownToken_returnsCriticalDefault() {
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TokenRiskProfile profile = registry.getProfile(UNKNOWN_CONTRACT);

        assertNotNull(profile);
        assertEquals(UNKNOWN_CONTRACT, profile.getContractAddress());
        assertTrue(profile.getCapabilities().isEmpty());
        assertEquals(RiskLevel.CRITICAL, profile.getRiskLevel());
        assertFalse(profile.isAdmissionPassed());
        assertFalse(profile.isAutoProcessingAllowed());
    }

    @Test
    void getProfile_addressNormalization_lowercased() {
        String mixedCase = "0xDAC17F958D2EE523A2206206994597C13D831EC7";

        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TokenRiskProfile profile = registry.getProfile(mixedCase);

        assertEquals(mixedCase.toLowerCase(), profile.getContractAddress());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProfile_secondCall_returnsCachedWithoutDbQuery() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("NO_RETURN_VALUE")
                .riskLevel("LOW")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile first = registry.getProfile(USDT_CONTRACT);
        TokenRiskProfile second = registry.getProfile(USDT_CONTRACT);

        assertSame(first, second);
        verify(tokenConfigMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildProfile_circuitBreakerStatusOpen_circuitBreakerOpenTrue() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("STANDARD_RETURN")
                .riskLevel("LOW")
                .enabled(1)
                .circuitBreakerStatus("OPEN")
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertTrue(profile.isCircuitBreakerOpen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildProfile_circuitBreakerStatusClosed_circuitBreakerOpenFalse() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("STANDARD_RETURN")
                .riskLevel("LOW")
                .enabled(1)
                .circuitBreakerStatus("CLOSED")
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertFalse(profile.isCircuitBreakerOpen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildProfile_circuitBreakerStatusNull_circuitBreakerOpenFalse() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("STANDARD_RETURN")
                .riskLevel("LOW")
                .enabled(1)
                .circuitBreakerStatus(null)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        TokenRiskProfile profile = registry.getProfile(USDT_CONTRACT);

        assertFalse(profile.isCircuitBreakerOpen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildDefaultProfile_unknownToken_circuitBreakerOpenFalse() {
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TokenRiskProfile profile = registry.getProfile(UNKNOWN_CONTRACT);

        assertFalse(profile.isCircuitBreakerOpen());
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidate_afterInvalidate_reloadsFromDb() {
        TokenConfig config = TokenConfig.builder()
                .contractAddress(USDT_CONTRACT)
                .capabilities("NO_RETURN_VALUE")
                .riskLevel("LOW")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(config);

        registry.getProfile(USDT_CONTRACT);
        registry.invalidate(USDT_CONTRACT);
        registry.getProfile(USDT_CONTRACT);

        verify(tokenConfigMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }
}
