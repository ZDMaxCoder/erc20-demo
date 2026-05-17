package com.erc20.platform.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskLevelTest {

    @Test
    void values_allDefined_shouldContainExactly4Values() {
        RiskLevel[] values = RiskLevel.values();
        assertEquals(4, values.length);
    }

    @Test
    void values_ordering_shouldBeLowMediumHighCritical() {
        RiskLevel[] values = RiskLevel.values();
        assertEquals(RiskLevel.LOW, values[0]);
        assertEquals(RiskLevel.MEDIUM, values[1]);
        assertEquals(RiskLevel.HIGH, values[2]);
        assertEquals(RiskLevel.CRITICAL, values[3]);
    }

    @Test
    void compareTo_mediumVsHigh_shouldBeNegative() {
        assertTrue(RiskLevel.MEDIUM.compareTo(RiskLevel.HIGH) < 0);
    }

    @Test
    void compareTo_lowVsCritical_shouldBeNegative() {
        assertTrue(RiskLevel.LOW.compareTo(RiskLevel.CRITICAL) < 0);
    }

    @Test
    void compareTo_highVsLow_shouldBePositive() {
        assertTrue(RiskLevel.HIGH.compareTo(RiskLevel.LOW) > 0);
    }

    @Test
    void compareTo_sameValue_shouldBeZero() {
        assertEquals(0, RiskLevel.MEDIUM.compareTo(RiskLevel.MEDIUM));
    }

    @Test
    void compareTo_fullOrdering_shouldBeConsistent() {
        assertTrue(RiskLevel.LOW.compareTo(RiskLevel.MEDIUM) < 0);
        assertTrue(RiskLevel.MEDIUM.compareTo(RiskLevel.HIGH) < 0);
        assertTrue(RiskLevel.HIGH.compareTo(RiskLevel.CRITICAL) < 0);
    }
}
