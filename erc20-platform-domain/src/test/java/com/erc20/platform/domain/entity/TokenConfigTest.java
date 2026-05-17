package com.erc20.platform.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenConfigTest {

    @Test
    void setCapabilities_validValue_getReturnsValue() {
        TokenConfig config = new TokenConfig();
        config.setCapabilities("STANDARD_RETURN,PAUSABLE,BLACKLISTABLE");
        assertEquals("STANDARD_RETURN,PAUSABLE,BLACKLISTABLE", config.getCapabilities());
    }

    @Test
    void setCapabilities_null_getReturnsNull() {
        TokenConfig config = new TokenConfig();
        config.setCapabilities(null);
        assertNull(config.getCapabilities());
    }

    @Test
    void setRiskLevel_validValue_getReturnsValue() {
        TokenConfig config = new TokenConfig();
        config.setRiskLevel("HIGH");
        assertEquals("HIGH", config.getRiskLevel());
    }

    @Test
    void setRiskLevel_null_getReturnsNull() {
        TokenConfig config = new TokenConfig();
        config.setRiskLevel(null);
        assertNull(config.getRiskLevel());
    }

    @Test
    void builder_withCapabilitiesAndRiskLevel_fieldsSet() {
        TokenConfig config = TokenConfig.builder()
                .capabilities("NO_RETURN_VALUE,APPROVE_RACE_CONDITION")
                .riskLevel("MEDIUM")
                .build();
        assertEquals("NO_RETURN_VALUE,APPROVE_RACE_CONDITION", config.getCapabilities());
        assertEquals("MEDIUM", config.getRiskLevel());
    }
}
