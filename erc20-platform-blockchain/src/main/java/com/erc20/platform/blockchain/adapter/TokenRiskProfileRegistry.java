package com.erc20.platform.blockchain.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TokenRiskProfileRegistry {

    private final TokenConfigMapper tokenConfigMapper;
    private final ConcurrentMap<String, TokenRiskProfile> cache = new ConcurrentHashMap<String, TokenRiskProfile>();

    public TokenRiskProfileRegistry(TokenConfigMapper tokenConfigMapper) {
        this.tokenConfigMapper = tokenConfigMapper;
    }

    public TokenRiskProfile getProfile(String contract) {
        String normalized = contract.toLowerCase();
        TokenRiskProfile cached = cache.get(normalized);
        if (cached != null) {
            return cached;
        }
        TokenConfig config = tokenConfigMapper.selectOne(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getContractAddress, normalized)
        );
        TokenRiskProfile profile = (config == null)
                ? buildDefaultProfile(normalized)
                : buildProfile(normalized, config);
        cache.put(normalized, profile);
        return profile;
    }

    public void invalidate(String contract) {
        cache.remove(contract.toLowerCase());
    }

    private TokenRiskProfile buildProfile(String contract, TokenConfig config) {
        Set<TokenCapability> capabilities = parseCapabilities(config.getCapabilities());
        RiskLevel riskLevel = parseRiskLevel(config.getRiskLevel());
        boolean admissionPassed = config.getEnabled() != null && config.getEnabled() == 1;
        boolean autoProcessingAllowed = riskLevel.compareTo(RiskLevel.MEDIUM) <= 0;

        boolean circuitBreakerOpen = "OPEN".equals(config.getCircuitBreakerStatus());

        return TokenRiskProfile.builder()
                .contractAddress(contract)
                .capabilities(capabilities)
                .riskLevel(riskLevel)
                .admissionPassed(admissionPassed)
                .autoProcessingAllowed(autoProcessingAllowed)
                .circuitBreakerOpen(circuitBreakerOpen)
                .build();
    }

    private TokenRiskProfile buildDefaultProfile(String contract) {
        return TokenRiskProfile.builder()
                .contractAddress(contract)
                .capabilities(Collections.<TokenCapability>emptySet())
                .riskLevel(RiskLevel.CRITICAL)
                .admissionPassed(false)
                .autoProcessingAllowed(false)
                .build();
    }

    private Set<TokenCapability> parseCapabilities(String capabilities) {
        if (capabilities == null || capabilities.trim().isEmpty()) {
            return EnumSet.of(TokenCapability.STANDARD_RETURN);
        }
        Set<TokenCapability> result = EnumSet.noneOf(TokenCapability.class);
        for (String cap : capabilities.split(",")) {
            String trimmed = cap.trim();
            if (!trimmed.isEmpty()) {
                result.add(TokenCapability.valueOf(trimmed));
            }
        }
        return result;
    }

    private RiskLevel parseRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            return RiskLevel.LOW;
        }
        return RiskLevel.valueOf(riskLevel.trim());
    }
}
