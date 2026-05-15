package com.erc20.platform.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressUtilTest {

    @Test
    void validLowercaseAddress_passes() {
        assertTrue(AddressUtil.isValid("0x1234567890abcdef1234567890abcdef12345678"));
    }

    @Test
    void validChecksumAddress_passes() {
        assertTrue(AddressUtil.isValid("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
    }

    @Test
    void invalidLength_tooShort_fails() {
        assertFalse(AddressUtil.isValid("0x12345678"));
    }

    @Test
    void invalidLength_tooLong_fails() {
        assertFalse(AddressUtil.isValid("0x1234567890abcdef1234567890abcdef1234567890"));
    }

    @Test
    void missingPrefix_fails() {
        assertFalse(AddressUtil.isValid("1234567890abcdef1234567890abcdef12345678"));
    }

    @Test
    void invalidHexChars_fails() {
        assertFalse(AddressUtil.isValid("0x1234567890abcdef1234567890abcdef1234567G"));
    }

    @Test
    void null_fails() {
        assertFalse(AddressUtil.isValid(null));
    }

    @Test
    void empty_fails() {
        assertFalse(AddressUtil.isValid(""));
    }

    @Test
    void normalize_returnsLowercase() {
        String mixed = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
        assertEquals("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed", AddressUtil.normalize(mixed));
    }

    @Test
    void normalize_alreadyLowercase() {
        String lower = "0x1234567890abcdef1234567890abcdef12345678";
        assertEquals(lower, AddressUtil.normalize(lower));
    }
}
