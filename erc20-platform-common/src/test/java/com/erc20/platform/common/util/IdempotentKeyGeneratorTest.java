package com.erc20.platform.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentKeyGeneratorTest {

    @Test
    void depositKey_includesChainId() {
        assertEquals("1_0xabc_3", IdempotentKeyGenerator.depositKey(1, "0xabc", 3));
    }

    @Test
    void depositKey_differentChainProducesDifferentKey() {
        assertEquals("56_0xabc_3", IdempotentKeyGenerator.depositKey(56, "0xabc", 3));
    }

    @Test
    void depositKey_zeroIndex() {
        assertEquals("1_0xdef_0", IdempotentKeyGenerator.depositKey(1, "0xdef", 0));
    }

    @Test
    void withdrawKey_includesChainId() {
        assertEquals("WD_1_req123", IdempotentKeyGenerator.withdrawKey(1, "req123"));
    }

    @Test
    void withdrawKey_differentChainProducesDifferentKey() {
        assertEquals("WD_137_req123", IdempotentKeyGenerator.withdrawKey(137, "req123"));
    }

    @Test
    void collectionKey_includesChainId() {
        assertEquals("COL_1_0xaddr_1_100", IdempotentKeyGenerator.collectionKey(1, "0xaddr", 1, 100));
    }

    @Test
    void collectionKey_differentChainProducesDifferentKey() {
        assertEquals("COL_56_0xaddr_1_100", IdempotentKeyGenerator.collectionKey(56, "0xaddr", 1, 100));
    }

    @Test
    void collectionKey_differentParams() {
        assertEquals("COL_1_0xabc_5_999", IdempotentKeyGenerator.collectionKey(1, "0xabc", 5, 999));
    }
}
