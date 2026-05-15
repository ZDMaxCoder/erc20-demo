package com.erc20.platform.blockchain.wallet;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BroadcastResult {

    private boolean success;
    private String txHash;
    private BroadcastErrorType errorType;
    private String errorMessage;

    public static BroadcastResult success(String txHash) {
        return BroadcastResult.builder()
                .success(true)
                .txHash(txHash)
                .errorType(BroadcastErrorType.NONE)
                .build();
    }

    public static BroadcastResult error(BroadcastErrorType errorType, String errorMessage) {
        return BroadcastResult.builder()
                .success(false)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .build();
    }
}
