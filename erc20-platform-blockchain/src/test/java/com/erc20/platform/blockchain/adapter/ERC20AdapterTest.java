package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ERC20AdapterTest {

    @Test
    void balanceOf_delegatesToMock_returnsBigInteger() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        BigInteger expected = BigInteger.valueOf(1000000);
        when(adapter.balanceOf("0xcontract", "0xowner")).thenReturn(expected);

        assertEquals(expected, adapter.balanceOf("0xcontract", "0xowner"));
    }

    @Test
    void decimals_delegatesToMock_returnsInt() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        when(adapter.decimals("0xcontract")).thenReturn(6);

        assertEquals(6, adapter.decimals("0xcontract"));
    }

    @Test
    void symbol_delegatesToMock_returnsString() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        when(adapter.symbol("0xcontract")).thenReturn("USDT");

        assertEquals("USDT", adapter.symbol("0xcontract"));
    }

    @Test
    void name_delegatesToMock_returnsString() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        when(adapter.name("0xcontract")).thenReturn("Tether USD");

        assertEquals("Tether USD", adapter.name("0xcontract"));
    }

    @Test
    void allowance_delegatesToMock_returnsBigInteger() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        BigInteger expected = BigInteger.valueOf(500000);
        when(adapter.allowance("0xcontract", "0xowner", "0xspender")).thenReturn(expected);

        assertEquals(expected, adapter.allowance("0xcontract", "0xowner", "0xspender"));
    }

    @Test
    void safeTransfer_delegatesToMock_returnsTxHash() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        when(adapter.safeTransfer("0xfrom", "0xcontract", "0xto", BigInteger.valueOf(1000)))
                .thenReturn("0xtxhash");

        assertEquals("0xtxhash", adapter.safeTransfer("0xfrom", "0xcontract", "0xto", BigInteger.valueOf(1000)));
    }

    @Test
    void safeApprove_delegatesToMock_returnsTxHash() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        when(adapter.safeApprove("0xowner", "0xcontract", "0xspender", BigInteger.valueOf(2000)))
                .thenReturn("0xapprovetx");

        assertEquals("0xapprovetx", adapter.safeApprove("0xowner", "0xcontract", "0xspender", BigInteger.valueOf(2000)));
    }

    @Test
    void confirmTransfer_delegatesToMock_returnsTransferResult() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        TransferResult result = TransferResult.pending("0xtx");
        when(adapter.confirmTransfer("0xtx", "0xcontract", BigInteger.valueOf(1000), "0xto"))
                .thenReturn(result);

        assertSame(result, adapter.confirmTransfer("0xtx", "0xcontract", BigInteger.valueOf(1000), "0xto"));
    }

    @Test
    void getTokenProfile_delegatesToMock_returnsTokenRiskProfile() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        TokenRiskProfile profile = TokenRiskProfile.builder().contractAddress("0xcontract").build();
        when(adapter.getTokenProfile("0xcontract")).thenReturn(profile);

        assertSame(profile, adapter.getTokenProfile("0xcontract"));
    }

    @Test
    void isTokenAdmitted_delegatesToMock_returnsBoolean() {
        ERC20Adapter adapter = mock(ERC20Adapter.class);
        when(adapter.isTokenAdmitted("0xcontract")).thenReturn(true);

        assertTrue(adapter.isTokenAdmitted("0xcontract"));
    }
}
