package com.erc20.platform.common.util;

public final class IdempotentKeyGenerator {

    private IdempotentKeyGenerator() {
    }

    public static String depositKey(int chainId, String txHash, int logIndex) {
        return chainId + "_" + txHash + "_" + logIndex;
    }

    public static String withdrawKey(int chainId, String requestId) {
        return "WD_" + chainId + "_" + requestId;
    }

    public static String collectionKey(int chainId, String fromAddress, long tokenId, long blockNumber) {
        return "COL_" + chainId + "_" + fromAddress + "_" + tokenId + "_" + blockNumber;
    }
}
