package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.ERC20Adapter;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.domain.entity.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawTransactionSenderImplTest {

    @Mock
    private ERC20Adapter erc20Adapter;

    @Mock
    private WalletService walletService;

    private WithdrawTransactionSenderImpl sender;

    @BeforeEach
    void setUp() {
        sender = new WithdrawTransactionSenderImpl(erc20Adapter, walletService);
    }

    @Test
    void sendERC20Transfer_delegatesToERC20Adapter_returnsTxHash() {
        String from = "0xfrom";
        String to = "0xto";
        String contract = "0xcontract";
        BigInteger amount = BigInteger.valueOf(1000000);
        String expectedTxHash = "0xabcdef1234567890";

        when(erc20Adapter.safeTransfer(from, contract, to, amount)).thenReturn(expectedTxHash);

        TransactionRecord result = sender.sendERC20Transfer(from, to, contract, amount);

        assertEquals(expectedTxHash, result.getTxHash());
        verify(erc20Adapter).safeTransfer(from, contract, to, amount);
        verifyNoInteractions(walletService);
    }

    @Test
    void sendERC20Transfer_adapterThrowsException_propagates() {
        String from = "0xfrom";
        String to = "0xto";
        String contract = "0xcontract";
        BigInteger amount = BigInteger.valueOf(1000000);

        when(erc20Adapter.safeTransfer(from, contract, to, amount))
                .thenThrow(new RuntimeException("Token not admitted"));

        assertThrows(RuntimeException.class,
                () -> sender.sendERC20Transfer(from, to, contract, amount));
        verifyNoInteractions(walletService);
    }

    @Test
    void queryTransactionStatus_delegatesToWalletService_returnsStatus() {
        String txHash = "0xabcdef";
        when(walletService.queryTransactionStatus(txHash)).thenReturn(TxStatus.CONFIRMED);

        TxStatus result = sender.queryTransactionStatus(txHash);

        assertEquals(TxStatus.CONFIRMED, result);
        verify(walletService).queryTransactionStatus(txHash);
    }
}
