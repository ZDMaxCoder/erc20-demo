package com.erc20.platform.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentKeyGeneratorTest {

    @Test
    void depositKey_format() {
        assertEquals("0xabc_3", IdempotentKeyGenerator.depositKey("0xabc", 3));
    }

    @Test
    void depositKey_zeroIndex() {
        assertEquals("0xdef_0", IdempotentKeyGenerator.depositKey("0xdef", 0));
    }

    @Test
    void withdrawKey_format() {
        assertEquals("WD_req123", IdempotentKeyGenerator.withdrawKey("req123"));
    }

    @Test
    void collectionKey_format() {
        assertEquals("COL_0xaddr_1_100", IdempotentKeyGenerator.collectionKey("0xaddr", 1, 100));
    }

    @Test
    void collectionKey_differentParams() {
        assertEquals("COL_0xabc_5_999", IdempotentKeyGenerator.collectionKey("0xabc", 5, 999));
    }
}
