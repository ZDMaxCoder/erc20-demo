package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.wallet.SafeTransferExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultERC20AdapterReadTest {

    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String OWNER = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String SPENDER = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";

    @Mock
    private SafeERC20Caller safeERC20Caller;
    @Mock
    private TokenAdmissionGateway tokenAdmissionGateway;
    @Mock
    private SafeTransferExecutor safeTransferExecutor;
    @Mock
    private TransferConfirmer transferConfirmer;
    @Mock
    private TokenRiskProfileRegistry tokenRiskProfileRegistry;
    @Mock
    private TokenMetadataCache tokenMetadataCache;

    private DefaultERC20Adapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultERC20Adapter(safeERC20Caller, tokenAdmissionGateway,
                safeTransferExecutor, transferConfirmer, tokenRiskProfileRegistry, tokenMetadataCache);
    }

    @Test
    void balanceOf_validContractAndOwner_delegatesToSafeERC20Caller() {
        BigInteger expected = new BigInteger("1000000");
        when(safeERC20Caller.safeBalanceOf(CONTRACT, OWNER)).thenReturn(expected);

        BigInteger result = adapter.balanceOf(CONTRACT, OWNER);

        assertEquals(expected, result);
        verify(safeERC20Caller).safeBalanceOf(CONTRACT, OWNER);
    }

    @Test
    void decimals_validContract_delegatesToTokenMetadataCache() {
        when(tokenMetadataCache.getDecimals(CONTRACT)).thenReturn(6);

        int result = adapter.decimals(CONTRACT);

        assertEquals(6, result);
        verify(tokenMetadataCache).getDecimals(CONTRACT);
    }

    @Test
    void symbol_validContract_delegatesToTokenMetadataCache() {
        when(tokenMetadataCache.getSymbol(CONTRACT)).thenReturn("USDT");

        String result = adapter.symbol(CONTRACT);

        assertEquals("USDT", result);
        verify(tokenMetadataCache).getSymbol(CONTRACT);
    }

    @Test
    void name_validContract_delegatesToTokenMetadataCache() {
        when(tokenMetadataCache.getName(CONTRACT)).thenReturn("Tether USD");

        String result = adapter.name(CONTRACT);

        assertEquals("Tether USD", result);
        verify(tokenMetadataCache).getName(CONTRACT);
    }

    @Test
    void allowance_validParams_delegatesToSafeERC20Caller() {
        BigInteger expected = new BigInteger("500000");
        when(safeERC20Caller.safeAllowance(CONTRACT, OWNER, SPENDER)).thenReturn(expected);

        BigInteger result = adapter.allowance(CONTRACT, OWNER, SPENDER);

        assertEquals(expected, result);
        verify(safeERC20Caller).safeAllowance(CONTRACT, OWNER, SPENDER);
    }
}
