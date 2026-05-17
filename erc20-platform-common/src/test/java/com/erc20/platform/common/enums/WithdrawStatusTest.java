package com.erc20.platform.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawStatusTest {

    @Test
    void anomaly_code_shouldBeANOMALY() {
        assertEquals("ANOMALY", WithdrawStatus.ANOMALY.getCode());
    }

    @Test
    void anomaly_description_shouldBeAmountMismatchDetected() {
        assertEquals("Amount mismatch detected", WithdrawStatus.ANOMALY.getDescription());
    }

    @Test
    void values_shouldContainANOMALY() {
        boolean found = false;
        for (WithdrawStatus status : WithdrawStatus.values()) {
            if (status == WithdrawStatus.ANOMALY) {
                found = true;
                break;
            }
        }
        assertTrue(found, "WithdrawStatus should contain ANOMALY");
    }
}
