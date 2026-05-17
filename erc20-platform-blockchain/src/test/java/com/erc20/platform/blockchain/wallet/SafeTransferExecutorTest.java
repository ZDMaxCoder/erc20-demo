package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.exception.TransferPreCheckFailedException;
import com.erc20.platform.blockchain.adapter.model.CallResult;
import com.erc20.platform.blockchain.adapter.rpc.ERC20RpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SafeTransferExecutorTest {

    @Mock
    private ERC20RpcClient erc20RpcClient;

    @Mock
    private WalletService walletService;

    private SafeTransferExecutor executor;

    private static final String FROM = "0xabc123";
    private static final String CONTRACT = "0xtoken456";
    private static final String TO = "0xdef789";
    private static final String SPENDER = "0xspender999";
    private static final BigInteger AMOUNT = BigInteger.valueOf(1000);
    private static final String TX_HASH = "0xtxhash001";

    @BeforeEach
    void setUp() {
        executor = new SafeTransferExecutor(erc20RpcClient, walletService);
    }

    @Test
    void executeTransfer_preCheckSuccess_delegatesToWalletService() {
        when(erc20RpcClient.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT))
                .thenReturn(CallResult.success());
        when(walletService.sendERC20TransferInternal(FROM, TO, CONTRACT, AMOUNT))
                .thenReturn(TX_HASH);

        String result = executor.executeTransfer(FROM, CONTRACT, TO, AMOUNT);

        assertEquals(TX_HASH, result);
        verify(erc20RpcClient).preCheckTransfer(CONTRACT, FROM, TO, AMOUNT);
        verify(walletService).sendERC20TransferInternal(FROM, TO, CONTRACT, AMOUNT);
    }

    @Test
    void executeTransfer_preCheckReturnedFalse_throwsTransferPreCheckFailedException() {
        CallResult falseResult = CallResult.returnedFalse();
        when(erc20RpcClient.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT))
                .thenReturn(falseResult);

        TransferPreCheckFailedException ex = assertThrows(
                TransferPreCheckFailedException.class,
                () -> executor.executeTransfer(FROM, CONTRACT, TO, AMOUNT));

        assertEquals(CONTRACT.toLowerCase(), ex.getContractAddress());
        assertSame(falseResult, ex.getCallResult());
        verify(walletService, never()).sendERC20TransferInternal(anyString(), anyString(), anyString(), any());
    }

    @Test
    void executeTransfer_preCheckReverted_throwsTransferPreCheckFailedException() {
        CallResult revertedResult = CallResult.reverted();
        when(erc20RpcClient.preCheckTransfer(CONTRACT, FROM, TO, AMOUNT))
                .thenReturn(revertedResult);

        TransferPreCheckFailedException ex = assertThrows(
                TransferPreCheckFailedException.class,
                () -> executor.executeTransfer(FROM, CONTRACT, TO, AMOUNT));

        assertEquals(CONTRACT.toLowerCase(), ex.getContractAddress());
        assertSame(revertedResult, ex.getCallResult());
        verify(walletService, never()).sendERC20TransferInternal(anyString(), anyString(), anyString(), any());
    }

    @Test
    void executeApprove_preCheckSuccess_delegatesToWalletService() {
        when(erc20RpcClient.preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT))
                .thenReturn(CallResult.success());
        when(walletService.sendApproveInternal(FROM, CONTRACT, SPENDER, AMOUNT))
                .thenReturn(TX_HASH);

        String result = executor.executeApprove(FROM, CONTRACT, SPENDER, AMOUNT);

        assertEquals(TX_HASH, result);
        verify(erc20RpcClient).preCheckApprove(CONTRACT, FROM, SPENDER, AMOUNT);
        verify(walletService).sendApproveInternal(FROM, CONTRACT, SPENDER, AMOUNT);
    }
}
