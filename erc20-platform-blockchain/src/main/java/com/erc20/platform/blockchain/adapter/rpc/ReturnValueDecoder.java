package com.erc20.platform.blockchain.adapter.rpc;

import com.erc20.platform.blockchain.adapter.model.CallResult;
import org.springframework.stereotype.Component;

@Component
public final class ReturnValueDecoder {

    private static final String HEX_PREFIX = "0x";
    private static final int ABI_ENCODED_BOOL_HEX_LENGTH = 64;
    private static final String BOOL_TRUE_PADDED = "0000000000000000000000000000000000000000000000000000000000000001";
    private static final String BOOL_FALSE_PADDED = "0000000000000000000000000000000000000000000000000000000000000000";

    public CallResult decodeBoolReturn(String hexResult) {
        if (hexResult == null || hexResult.isEmpty() || HEX_PREFIX.equals(hexResult)) {
            return CallResult.successNoReturn();
        }

        String stripped = hexResult.startsWith(HEX_PREFIX)
                ? hexResult.substring(HEX_PREFIX.length())
                : hexResult;

        if (stripped.length() < ABI_ENCODED_BOOL_HEX_LENGTH) {
            return CallResult.successNoReturn();
        }

        if (BOOL_TRUE_PADDED.equals(stripped)) {
            return CallResult.success();
        }

        if (BOOL_FALSE_PADDED.equals(stripped)) {
            return CallResult.returnedFalse();
        }

        String normalized = hexResult.startsWith(HEX_PREFIX) ? hexResult : HEX_PREFIX + hexResult;
        return CallResult.unknown(normalized);
    }

    @Override
    public String toString() {
        return "ReturnValueDecoder{}";
    }
}
