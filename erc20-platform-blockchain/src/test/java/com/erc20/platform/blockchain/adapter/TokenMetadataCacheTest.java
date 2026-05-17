package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenMetadataCacheTest {

    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    @Mock
    private SafeERC20Caller safeERC20Caller;

    private TokenMetadataCache cache;

    @BeforeEach
    void setUp() {
        cache = new TokenMetadataCache(safeERC20Caller);
    }

    @Test
    void getDecimals_firstCall_triggersRpc() {
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);

        int result = cache.getDecimals(CONTRACT);

        assertEquals(6, result);
        verify(safeERC20Caller, times(1)).safeDecimals(CONTRACT);
    }

    @Test
    void getDecimals_secondCall_returnsCachedWithoutRpc() {
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);

        cache.getDecimals(CONTRACT);
        int result = cache.getDecimals(CONTRACT);

        assertEquals(6, result);
        verify(safeERC20Caller, times(1)).safeDecimals(CONTRACT);
    }

    @Test
    void getSymbol_firstCall_triggersRpc() {
        when(safeERC20Caller.safeSymbol(CONTRACT)).thenReturn("USDT");

        String result = cache.getSymbol(CONTRACT);

        assertEquals("USDT", result);
        verify(safeERC20Caller, times(1)).safeSymbol(CONTRACT);
    }

    @Test
    void getSymbol_secondCall_returnsCachedWithoutRpc() {
        when(safeERC20Caller.safeSymbol(CONTRACT)).thenReturn("USDT");

        cache.getSymbol(CONTRACT);
        String result = cache.getSymbol(CONTRACT);

        assertEquals("USDT", result);
        verify(safeERC20Caller, times(1)).safeSymbol(CONTRACT);
    }

    @Test
    void getName_firstCall_triggersRpc() {
        when(safeERC20Caller.safeName(CONTRACT)).thenReturn("Tether USD");

        String result = cache.getName(CONTRACT);

        assertEquals("Tether USD", result);
        verify(safeERC20Caller, times(1)).safeName(CONTRACT);
    }

    @Test
    void getName_secondCall_returnsCachedWithoutRpc() {
        when(safeERC20Caller.safeName(CONTRACT)).thenReturn("Tether USD");

        cache.getName(CONTRACT);
        String result = cache.getName(CONTRACT);

        assertEquals("Tether USD", result);
        verify(safeERC20Caller, times(1)).safeName(CONTRACT);
    }

    @Test
    void getSymbol_afterGetDecimals_triggersRpcForSymbol() {
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);
        when(safeERC20Caller.safeSymbol(CONTRACT)).thenReturn("USDT");

        cache.getDecimals(CONTRACT);
        String result = cache.getSymbol(CONTRACT);

        assertEquals("USDT", result);
        verify(safeERC20Caller, times(1)).safeSymbol(CONTRACT);
    }

    @Test
    void invalidate_afterInvalidate_nextCallTriggersRpc() {
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);

        cache.getDecimals(CONTRACT);
        cache.invalidate(CONTRACT);
        cache.getDecimals(CONTRACT);

        verify(safeERC20Caller, times(2)).safeDecimals(CONTRACT);
    }

    @Test
    void invalidate_clearsAllMetadataForContract() {
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);
        when(safeERC20Caller.safeSymbol(CONTRACT)).thenReturn("USDT");
        when(safeERC20Caller.safeName(CONTRACT)).thenReturn("Tether USD");

        cache.getDecimals(CONTRACT);
        cache.getSymbol(CONTRACT);
        cache.getName(CONTRACT);
        cache.invalidate(CONTRACT);
        cache.getDecimals(CONTRACT);
        cache.getSymbol(CONTRACT);
        cache.getName(CONTRACT);

        verify(safeERC20Caller, times(2)).safeDecimals(CONTRACT);
        verify(safeERC20Caller, times(2)).safeSymbol(CONTRACT);
        verify(safeERC20Caller, times(2)).safeName(CONTRACT);
    }

    @Test
    void getDecimals_mixedCaseAddress_normalizedToLowercase() {
        String mixedCase = "0xDAC17F958D2EE523A2206206994597C13D831EC7";
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);

        cache.getDecimals(CONTRACT);
        int result = cache.getDecimals(mixedCase);

        assertEquals(6, result);
        verify(safeERC20Caller, times(1)).safeDecimals(CONTRACT);
    }

    @Test
    void invalidate_mixedCaseAddress_clearsCacheForNormalizedAddress() {
        String mixedCase = "0xDAC17F958D2EE523A2206206994597C13D831EC7";
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);

        cache.getDecimals(CONTRACT);
        cache.invalidate(mixedCase);
        cache.getDecimals(CONTRACT);

        verify(safeERC20Caller, times(2)).safeDecimals(CONTRACT);
    }
}
