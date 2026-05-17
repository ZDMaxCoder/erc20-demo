package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class TokenMetadataCache {

    private final SafeERC20Caller safeERC20Caller;
    private final ConcurrentMap<String, Integer> decimalsCache = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentMap<String, String> symbolCache = new ConcurrentHashMap<String, String>();
    private final ConcurrentMap<String, String> nameCache = new ConcurrentHashMap<String, String>();

    public TokenMetadataCache(SafeERC20Caller safeERC20Caller) {
        this.safeERC20Caller = safeERC20Caller;
    }

    public int getDecimals(String contract) {
        String normalized = contract.toLowerCase();
        Integer cached = decimalsCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        int value = safeERC20Caller.safeDecimals(normalized);
        decimalsCache.put(normalized, value);
        return value;
    }

    public String getSymbol(String contract) {
        String normalized = contract.toLowerCase();
        String cached = symbolCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        String value = safeERC20Caller.safeSymbol(normalized);
        symbolCache.put(normalized, value);
        return value;
    }

    public String getName(String contract) {
        String normalized = contract.toLowerCase();
        String cached = nameCache.get(normalized);
        if (cached != null) {
            return cached;
        }
        String value = safeERC20Caller.safeName(normalized);
        nameCache.put(normalized, value);
        return value;
    }

    public void invalidate(String contract) {
        String normalized = contract.toLowerCase();
        decimalsCache.remove(normalized);
        symbolCache.remove(normalized);
        nameCache.remove(normalized);
        log.info("Invalidated metadata cache for contract: {}", normalized);
    }
}
