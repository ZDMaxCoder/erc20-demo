package com.erc20.platform.blockchain.erc20;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafeERC20CallerTest {

    @Mock
    private Web3j web3j;

    @Mock
    private Request<?, EthCall> ethCallRequest;

    private SafeERC20Caller caller;

    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String OWNER = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @BeforeEach
    void setUp() {
        caller = new SafeERC20Caller(web3j);
    }

    @Test
    void safeBalanceOf_standardResponse_returnsCorrectBigInteger() throws Exception {
        String balanceHex = "0x0000000000000000000000000000000000000000000000000000000005f5e100";
        EthCall ethCall = new EthCall();
        ethCall.setResult(balanceHex);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        BigInteger result = caller.safeBalanceOf(CONTRACT, OWNER);

        assertEquals(new BigInteger("100000000"), result);
    }

    @Test
    void safeDecimals_standardUint8_returnsCorrectInt() throws Exception {
        String decimalsHex = "0x0000000000000000000000000000000000000000000000000000000000000006";
        EthCall ethCall = new EthCall();
        ethCall.setResult(decimalsHex);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        int result = caller.safeDecimals(CONTRACT);

        assertEquals(6, result);
    }

    @Test
    void safeDecimals_bytes32NonStandard_returnsCorrectInt() throws Exception {
        // bytes32 encoding of decimals=8 (some tokens return this)
        String decimalsBytes32 = "0x0000000000000000000000000000000000000000000000000000000000000008";
        EthCall ethCall = new EthCall();
        ethCall.setResult(decimalsBytes32);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        int result = caller.safeDecimals(CONTRACT);

        assertEquals(8, result);
    }

    @Test
    void safeDecimals_failure_returnsDefault18() throws Exception {
        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenThrow(new RuntimeException("RPC error"));

        int result = caller.safeDecimals(CONTRACT);

        assertEquals(18, result);
    }

    @Test
    void safeSymbol_standardString_returnsCorrectValue() throws Exception {
        // ABI-encoded string "USDT":
        // offset (32 bytes) + length (32 bytes) + data (32 bytes)
        String symbolHex = "0x"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "5553445400000000000000000000000000000000000000000000000000000000";
        EthCall ethCall = new EthCall();
        ethCall.setResult(symbolHex);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        String result = caller.safeSymbol(CONTRACT);

        assertEquals("USDT", result);
    }

    @Test
    void safeSymbol_bytes32WithNullPadding_returnsCorrectString() throws Exception {
        // bytes32-encoded "MKR" with null padding (non-standard)
        String symbolBytes32 = "0x"
                + "4d4b520000000000000000000000000000000000000000000000000000000000";
        EthCall ethCall = new EthCall();
        ethCall.setResult(symbolBytes32);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        String result = caller.safeSymbol(CONTRACT);

        assertEquals("MKR", result);
    }
}
