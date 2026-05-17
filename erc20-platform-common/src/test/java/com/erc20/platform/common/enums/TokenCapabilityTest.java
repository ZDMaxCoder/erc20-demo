package com.erc20.platform.common.enums;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenCapabilityTest {

    @Test
    void values_allDefined_shouldContainExactly13Values() {
        TokenCapability[] values = TokenCapability.values();
        assertEquals(13, values.length);
    }

    @Test
    void values_allDefined_shouldContainAllExpectedConstants() {
        Set<TokenCapability> all = EnumSet.allOf(TokenCapability.class);
        assertTrue(all.contains(TokenCapability.STANDARD_RETURN));
        assertTrue(all.contains(TokenCapability.NO_RETURN_VALUE));
        assertTrue(all.contains(TokenCapability.APPROVE_RACE_CONDITION));
        assertTrue(all.contains(TokenCapability.BYTES32_METADATA));
        assertTrue(all.contains(TokenCapability.PAUSABLE));
        assertTrue(all.contains(TokenCapability.BLACKLISTABLE));
        assertTrue(all.contains(TokenCapability.UPGRADEABLE));
        assertTrue(all.contains(TokenCapability.MINTABLE));
        assertTrue(all.contains(TokenCapability.BURNABLE));
        assertTrue(all.contains(TokenCapability.FEE_ON_TRANSFER));
        assertTrue(all.contains(TokenCapability.REBASING));
        assertTrue(all.contains(TokenCapability.MAX_TRANSFER_LIMIT));
        assertTrue(all.contains(TokenCapability.COOLDOWN_REQUIRED));
    }

    @Test
    void enumSet_of_shouldContainExactlySpecifiedValues() {
        EnumSet<TokenCapability> set = EnumSet.of(
                TokenCapability.NO_RETURN_VALUE,
                TokenCapability.APPROVE_RACE_CONDITION
        );
        assertEquals(2, set.size());
        assertTrue(set.contains(TokenCapability.NO_RETURN_VALUE));
        assertTrue(set.contains(TokenCapability.APPROVE_RACE_CONDITION));
        assertFalse(set.contains(TokenCapability.STANDARD_RETURN));
    }

    @Test
    void getCode_eachValue_shouldReturnMatchingName() {
        for (TokenCapability cap : TokenCapability.values()) {
            assertEquals(cap.name(), cap.getCode());
        }
    }

    @Test
    void getDescription_eachValue_shouldReturnNonEmptyString() {
        for (TokenCapability cap : TokenCapability.values()) {
            assertNotNull(cap.getDescription());
            assertFalse(cap.getDescription().isEmpty());
        }
    }
}
