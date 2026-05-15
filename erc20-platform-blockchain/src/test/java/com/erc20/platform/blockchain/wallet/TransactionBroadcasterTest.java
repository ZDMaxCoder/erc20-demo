package com.erc20.platform.blockchain.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionBroadcasterTest {

    @Mock
    private Web3j web3j;
    @Mock
    private Request<?, EthSendTransaction> sendTxRequest;

    private TransactionBroadcaster broadcaster;

    private static final String SIGNED_TX_HEX = "0xf86c0a8502540be400825208949b9e0b0c5b5e4e4a0b8f1e4c7c0b0c5b5e4e4a0b880de0b6b3a76400008025a0123456a0654321";
    private static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

    @BeforeEach
    void setUp() {
        broadcaster = new TransactionBroadcaster(web3j);
    }

    // --- 3.1 Successful broadcast returns txHash ---

    @Test
    void broadcast_success_returnsTxHash() throws Exception {
        EthSendTransaction response = new EthSendTransaction();
        response.setResult(TX_HASH);
        mockSendRawTransaction(response);

        BroadcastResult result = broadcaster.broadcast(SIGNED_TX_HEX);

        assertTrue(result.isSuccess());
        assertEquals(TX_HASH, result.getTxHash());
        assertEquals(BroadcastErrorType.NONE, result.getErrorType());
    }

    // --- 3.2 "nonce too low" → NONCE_TOO_LOW ---

    @Test
    void broadcast_nonceTooLow_returnsNonceTooLowError() throws Exception {
        EthSendTransaction response = createErrorResponse(-32000, "nonce too low");
        mockSendRawTransaction(response);

        BroadcastResult result = broadcaster.broadcast(SIGNED_TX_HEX);

        assertFalse(result.isSuccess());
        assertEquals(BroadcastErrorType.NONCE_TOO_LOW, result.getErrorType());
    }

    // --- 3.3 "already known" → treated as success (ALREADY_KNOWN) ---

    @Test
    void broadcast_alreadyKnown_treatedAsSuccess() throws Exception {
        EthSendTransaction response = createErrorResponse(-32000, "already known");
        mockSendRawTransaction(response);

        BroadcastResult result = broadcaster.broadcast(SIGNED_TX_HEX);

        assertTrue(result.isSuccess());
        assertEquals(BroadcastErrorType.ALREADY_KNOWN, result.getErrorType());
    }

    // --- 3.4 "replacement transaction underpriced" → UNDERPRICED ---

    @Test
    void broadcast_replacementUnderpriced_returnsUnderpricedError() throws Exception {
        EthSendTransaction response = createErrorResponse(-32000, "replacement transaction underpriced");
        mockSendRawTransaction(response);

        BroadcastResult result = broadcaster.broadcast(SIGNED_TX_HEX);

        assertFalse(result.isSuccess());
        assertEquals(BroadcastErrorType.UNDERPRICED, result.getErrorType());
    }

    // --- 3.5 "insufficient funds" → INSUFFICIENT_FUNDS ---

    @Test
    void broadcast_insufficientFunds_returnsInsufficientFundsError() throws Exception {
        EthSendTransaction response = createErrorResponse(-32000, "insufficient funds for gas * price + value");
        mockSendRawTransaction(response);

        BroadcastResult result = broadcaster.broadcast(SIGNED_TX_HEX);

        assertFalse(result.isSuccess());
        assertEquals(BroadcastErrorType.INSUFFICIENT_FUNDS, result.getErrorType());
    }

    // --- Helper ---

    private void mockSendRawTransaction(EthSendTransaction response) throws Exception {
        doReturn(sendTxRequest).when(web3j).ethSendRawTransaction(anyString());
        doReturn(response).when(sendTxRequest).send();
    }

    private EthSendTransaction createErrorResponse(int code, String message) {
        EthSendTransaction response = new EthSendTransaction();
        Response.Error error = new Response.Error(code, message);
        response.setError(error);
        return response;
    }
}
