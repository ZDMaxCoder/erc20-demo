package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.ERC20Adapter;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.gas.GasEstimator;
import com.erc20.platform.blockchain.gas.GasPrice;
import com.erc20.platform.blockchain.gas.GasPriceCache;
import com.erc20.platform.blockchain.gas.GasPriority;
import com.erc20.platform.domain.entity.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetBalance;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionTransactionSenderImplTest {

    @Mock
    private ERC20Adapter erc20Adapter;

    @Mock
    private WalletService walletService;

    @Mock
    private SafeERC20Caller safeERC20Caller;

    @Mock
    private GasEstimator gasEstimator;

    @Mock
    private GasPriceCache gasPriceCache;

    @Mock
    private Web3j web3j;

    private CollectionTransactionSenderImpl sender;

    @BeforeEach
    void setUp() {
        sender = new CollectionTransactionSenderImpl(erc20Adapter, walletService,
                safeERC20Caller, gasEstimator, gasPriceCache, web3j);
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
        verify(walletService, never()).sendERC20Transfer(anyString(), anyString(), anyString(),
                any(BigInteger.class), any(GasPriority.class));
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
        verify(walletService, never()).sendERC20Transfer(anyString(), anyString(), anyString(),
                any(BigInteger.class), any(GasPriority.class));
    }

    @Test
    void sendEthTransfer_delegatesToWalletService_returnsRecord() {
        String from = "0xfrom";
        String to = "0xto";
        BigInteger amountWei = BigInteger.valueOf(1000000000000000000L);
        TransactionRecord expected = TransactionRecord.builder().txHash("0xeth123").build();

        when(walletService.sendEthTransfer(from, to, amountWei, GasPriority.MEDIUM))
                .thenReturn(expected);

        TransactionRecord result = sender.sendEthTransfer(from, to, amountWei);

        assertEquals("0xeth123", result.getTxHash());
        verify(walletService).sendEthTransfer(from, to, amountWei, GasPriority.MEDIUM);
    }

    @Test
    void getERC20Balance_delegatesToSafeERC20Caller_returnsBalance() {
        String contract = "0xcontract";
        String owner = "0xowner";
        BigInteger expectedBalance = BigInteger.valueOf(5000000);

        when(safeERC20Caller.safeBalanceOf(contract, owner)).thenReturn(expectedBalance);

        BigInteger result = sender.getERC20Balance(contract, owner);

        assertEquals(expectedBalance, result);
        verify(safeERC20Caller).safeBalanceOf(contract, owner);
    }
}
