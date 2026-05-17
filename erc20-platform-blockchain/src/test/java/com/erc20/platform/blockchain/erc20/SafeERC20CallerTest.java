package com.erc20.platform.blockchain.erc20;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private static final String SPENDER = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @BeforeEach
    void setUp() {
        caller = new SafeERC20Caller(web3j, "");
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

    @Test
    void safeBalanceOf_emptyResponse_throwsRuntimeException() throws Exception {
        EthCall ethCallResponse = new EthCall();
        ethCallResponse.setResult("0x");

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCallResponse);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> caller.safeBalanceOf(CONTRACT, OWNER));
        assertTrue(ex.getMessage().contains("balanceOf returned empty"));
    }

    @Test
    void safeDecimals_failure_throwsRuntimeException() throws Exception {
        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenThrow(new RuntimeException("RPC error"));

        assertThrows(RuntimeException.class, () -> caller.safeDecimals(CONTRACT));
    }

    @Test
    void ethCall_ioException_throwsChainCallExceptionImmediately() throws Exception {
        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenThrow(new IOException("connection reset"));

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> caller.safeBalanceOf(CONTRACT, OWNER));
        assertEquals(CONTRACT, ex.getContract());
        assertTrue(ex.getCause() instanceof IOException);
        verify(ethCallRequest, times(1)).send();
    }

    @Test
    void ethCall_responseError_throwsChainCallException() throws Exception {
        EthCall errorResponse = new EthCall();
        errorResponse.setError(new org.web3j.protocol.core.Response.Error(3, "execution reverted"));

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(errorResponse);

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> caller.safeBalanceOf(CONTRACT, OWNER));
        assertEquals(CONTRACT, ex.getContract());
        assertTrue(ex.getMessage().contains("execution reverted"));
    }

    @Test
    void safeTransfer_methodRemoved_noSuchMethod() {
        assertThrows(NoSuchMethodException.class,
                () -> SafeERC20Caller.class.getMethod("safeTransfer", String.class, String.class, BigInteger.class));
    }

    @Test
    void safeApprove_methodRemoved_noSuchMethod() {
        assertThrows(NoSuchMethodException.class,
                () -> SafeERC20Caller.class.getMethod("safeApprove", String.class, String.class, BigInteger.class));
    }

    @Test
    void safeAllowance_standardResponse_returnsCorrectBigInteger() throws Exception {
        String allowanceHex = "0x00000000000000000000000000000000000000000000000000000000001e8480";
        EthCall ethCall = new EthCall();
        ethCall.setResult(allowanceHex);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        BigInteger result = caller.safeAllowance(CONTRACT, OWNER, SPENDER);

        assertEquals(new BigInteger("2000000"), result);
    }

    @Test
    void safeAllowance_emptyResponse_throwsRuntimeException() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult("0x");

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> caller.safeAllowance(CONTRACT, OWNER, SPENDER));
        assertTrue(ex.getMessage().contains("allowance returned empty"));
    }

    @Test
    void safeAllowance_zeroAllowance_returnsBigIntegerZero() throws Exception {
        String zeroHex = "0x0000000000000000000000000000000000000000000000000000000000000000";
        EthCall ethCall = new EthCall();
        ethCall.setResult(zeroHex);

        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenReturn(ethCall);

        BigInteger result = caller.safeAllowance(CONTRACT, OWNER, SPENDER);

        assertEquals(BigInteger.ZERO, result);
    }

    @Test
    void safeAllowance_ioException_throwsChainCallException() throws Exception {
        doReturn(ethCallRequest).when(web3j).ethCall(any(), any());
        when(ethCallRequest.send()).thenThrow(new IOException("connection timeout"));

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> caller.safeAllowance(CONTRACT, OWNER, SPENDER));
        assertEquals(CONTRACT, ex.getContract());
        assertTrue(ex.getCause() instanceof IOException);
        verify(ethCallRequest, times(1)).send();
    }

    @Test
    void decodeBytes32AsString_dynamicOffset_decodesCorrectly() throws Exception {
        String hexData = "0x"
                + "0000000000000000000000000000000000000000000000000000000000000020"
                + "0000000000000000000000000000000000000000000000000000000000000004"
                + "5553445400000000000000000000000000000000000000000000000000000000";

        Method method = SafeERC20Caller.class.getDeclaredMethod("decodeBytes32AsString", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(caller, hexData);

        assertEquals("USDT", result);
    }
}
