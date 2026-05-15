package com.erc20.platform.blockchain.erc20;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenMetadataReaderTest {

    @Mock
    private SafeERC20Caller safeERC20Caller;

    private TokenMetadataReader reader;

    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    @BeforeEach
    void setUp() {
        reader = new TokenMetadataReader(safeERC20Caller);
    }

    @Test
    void read_allFieldsSuccessful_returnsCompleteTokenMetadata() {
        when(safeERC20Caller.safeName(CONTRACT)).thenReturn("Tether USD");
        when(safeERC20Caller.safeSymbol(CONTRACT)).thenReturn("USDT");
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenReturn(6);

        TokenMetadata metadata = reader.read(CONTRACT);

        assertEquals("Tether USD", metadata.getName());
        assertEquals("USDT", metadata.getSymbol());
        assertEquals(6, metadata.getDecimals());
    }

    @Test
    void read_decimalsFails_returnsMetadataWithNullDecimals() {
        when(safeERC20Caller.safeName(CONTRACT)).thenReturn("Tether USD");
        when(safeERC20Caller.safeSymbol(CONTRACT)).thenReturn("USDT");
        when(safeERC20Caller.safeDecimals(CONTRACT)).thenThrow(new RuntimeException("RPC error"));

        TokenMetadata metadata = reader.read(CONTRACT);

        assertEquals("Tether USD", metadata.getName());
        assertEquals("USDT", metadata.getSymbol());
        assertNull(metadata.getDecimals());
    }
}
