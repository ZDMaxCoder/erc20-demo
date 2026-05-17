package com.erc20.platform.blockchain.adapter.rpc;

import com.erc20.platform.blockchain.adapter.model.CallOutcome;
import com.erc20.platform.blockchain.adapter.model.CallResult;
import com.erc20.platform.blockchain.erc20.ChainCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthCall;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ERC20RpcClientTest {

    private static final String CONTRACT = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String FROM = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TO = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SPENDER = "0xcccccccccccccccccccccccccccccccccccccccc";
    private static final BigInteger AMOUNT = BigInteger.valueOf(1000000);
    private static final String BOOL_TRUE_HEX = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private static final String BOOL_FALSE_HEX = "0x0000000000000000000000000000000000000000000000000000000000000000";

    @Mock
    private Web3j web3j;

    @SuppressWarnings("rawtypes")
    @Mock
    private Request request;

    private ERC20RpcClient client;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        ReturnValueDecoder decoder = new ReturnValueDecoder();
        client = new ERC20RpcClient(web3j, decoder);
        when(web3j.ethCall(any(), any())).thenReturn(request);
    }

    @Test
    void preCheckTransfer_standardTrue_shouldReturnSuccess() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult(BOOL_TRUE_HEX);
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT);

        assertTrue(result.isSuccess());
        assertEquals(CallOutcome.SUCCESS, result.getOutcome());
    }

    @Test
    void preCheckTransfer_emptyReturnUSDT_shouldReturnSuccessNoReturn() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult("0x");
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT);

        assertTrue(result.isSuccess());
        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
    }

    @Test
    void preCheckTransfer_returnsFalse_shouldReturnDangerousFalse() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult(BOOL_FALSE_HEX);
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT);

        assertTrue(result.isDangerousFalse());
        assertFalse(result.isSuccess());
        assertEquals(CallOutcome.RETURNED_FALSE, result.getOutcome());
    }

    @Test
    void preCheckTransfer_executionReverted_shouldReturnReverted() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setError(new org.web3j.protocol.core.Response.Error(3, "execution reverted: insufficient balance"));
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT);

        assertEquals(CallOutcome.REVERTED, result.getOutcome());
        assertFalse(result.isSuccess());
    }

    @Test
    void preCheckApprove_returnsTrue_shouldReturnSuccess() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult(BOOL_TRUE_HEX);
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT);

        assertTrue(result.isSuccess());
        assertEquals(CallOutcome.SUCCESS, result.getOutcome());
    }

    @Test
    void preCheckApprove_executionReverted_shouldReturnReverted() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setError(new org.web3j.protocol.core.Response.Error(3, "execution reverted: non-zero allowance"));
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT);

        assertEquals(CallOutcome.REVERTED, result.getOutcome());
        assertFalse(result.isSuccess());
    }

    @Test
    void preCheckApprove_emptyReturnUSDT_shouldReturnSuccessNoReturn() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult("0x");
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT);

        assertTrue(result.isSuccess());
        assertEquals(CallOutcome.SUCCESS_NO_RETURN, result.getOutcome());
    }

    @Test
    void preCheckApprove_returnsFalse_shouldReturnDangerousFalse() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setResult(BOOL_FALSE_HEX);
        when(request.send()).thenReturn(ethCall);

        CallResult result = client.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT);

        assertTrue(result.isDangerousFalse());
        assertFalse(result.isSuccess());
        assertEquals(CallOutcome.RETURNED_FALSE, result.getOutcome());
    }

    @Test
    void preCheckTransfer_ioException_shouldThrowChainCallException() throws Exception {
        when(request.send()).thenThrow(new IOException("connection refused"));

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> client.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT));

        assertEquals(CONTRACT, ex.getContract());
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof IOException);
    }

    @Test
    void preCheckApprove_ioException_shouldThrowChainCallException() throws Exception {
        when(request.send()).thenThrow(new IOException("connection timeout"));

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> client.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT));

        assertEquals(CONTRACT, ex.getContract());
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof IOException);
    }

    @Test
    void preCheckTransfer_rpcErrorNotReverted_shouldThrowChainCallException() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setError(new org.web3j.protocol.core.Response.Error(-32000, "nonce too low"));
        when(request.send()).thenReturn(ethCall);

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> client.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT));

        assertEquals(CONTRACT, ex.getContract());
        assertTrue(ex.getMessage().contains("nonce too low"));
    }

    @Test
    void preCheckApprove_rpcErrorNotReverted_shouldThrowChainCallException() throws Exception {
        EthCall ethCall = new EthCall();
        ethCall.setError(new org.web3j.protocol.core.Response.Error(-32000, "gas limit exceeded"));
        when(request.send()).thenReturn(ethCall);

        ChainCallException ex = assertThrows(ChainCallException.class,
                () -> client.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT));

        assertEquals(CONTRACT, ex.getContract());
        assertTrue(ex.getMessage().contains("gas limit exceeded"));
    }
}
