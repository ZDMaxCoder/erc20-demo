package com.erc20.platform.blockchain.erc20;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenMetadataReader {

    private final SafeERC20Caller safeERC20Caller;

    public TokenMetadataReader(SafeERC20Caller safeERC20Caller) {
        this.safeERC20Caller = safeERC20Caller;
    }

    public TokenMetadata read(String contract) {
        String name = readName(contract);
        String symbol = readSymbol(contract);
        Integer decimals = readDecimals(contract);

        return TokenMetadata.builder()
                .name(name)
                .symbol(symbol)
                .decimals(decimals)
                .build();
    }

    private String readName(String contract) {
        try {
            return safeERC20Caller.safeName(contract);
        } catch (Exception e) {
            log.warn("Failed to read name for {}: {}", contract, e.getMessage());
            return null;
        }
    }

    private String readSymbol(String contract) {
        try {
            return safeERC20Caller.safeSymbol(contract);
        } catch (Exception e) {
            log.warn("Failed to read symbol for {}: {}", contract, e.getMessage());
            return null;
        }
    }

    private Integer readDecimals(String contract) {
        try {
            return safeERC20Caller.safeDecimals(contract);
        } catch (Exception e) {
            log.warn("Failed to read decimals for {}: {}", contract, e.getMessage());
            return null;
        }
    }
}
