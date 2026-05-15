package com.erc20.platform.common.util;

public final class IdempotentKeyGenerator {

    private IdempotentKeyGenerator() {
    }

    public static String depositKey(String txHash, int logIndex) {
        return txHash + "_" + logIndex;
    }

    public static String withdrawKey(String requestId) {
        return "WD_" + requestId;
    }

    public static String collectionKey(String fromAddress, long tokenId, long blockNumber) {
        return "COL_" + fromAddress + "_" + tokenId + "_" + blockNumber;
    }
}
