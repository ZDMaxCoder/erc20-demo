package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.DefaultERC20Adapter;
import com.erc20.platform.blockchain.adapter.TokenAdmissionGateway;
import com.erc20.platform.blockchain.adapter.TokenRiskProfileRegistry;
import com.erc20.platform.blockchain.adapter.TransferConfirmer;
import com.erc20.platform.blockchain.adapter.exception.TokenAdmissionRejectedException;
import com.erc20.platform.blockchain.adapter.exception.TransferPreCheckFailedException;
import com.erc20.platform.blockchain.adapter.model.CallResult;
import com.erc20.platform.blockchain.adapter.rpc.ERC20RpcClient;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawTransactionSenderIntegrationTest {

    @Mock
    private TokenConfigMapper tokenConfigMapper;

    @Mock
    private ERC20RpcClient erc20RpcClient;

    @Mock
    private WalletService walletService;

    @Mock
    private SafeERC20Caller safeERC20Caller;

    @Mock
    private TransferConfirmer transferConfirmer;

    private WithdrawTransactionSenderImpl sender;

    @BeforeEach
    void setUp() {
        TokenRiskProfileRegistry registry = new TokenRiskProfileRegistry(tokenConfigMapper);
        TokenAdmissionGateway gateway = new TokenAdmissionGateway(registry);
        SafeTransferExecutor executor = new SafeTransferExecutor(erc20RpcClient, walletService);
        com.erc20.platform.blockchain.adapter.TokenMetadataCache metadataCache =
                new com.erc20.platform.blockchain.adapter.TokenMetadataCache(safeERC20Caller);
        DefaultERC20Adapter adapter = new DefaultERC20Adapter(
                safeERC20Caller, gateway, executor, transferConfirmer, registry, metadataCache);
        sender = new WithdrawTransactionSenderImpl(adapter, walletService);
    }

    @Test
    void sendERC20Transfer_standardToken_fullChainReturnsTransactionRecord() {
        String from = "0xhotwallet";
        String to = "0xuser";
        String contract = "0xusdt";
        BigInteger amount = BigInteger.valueOf(1000000);
        String expectedTxHash = "0xabc123";

        TokenConfig config = TokenConfig.builder()
                .contractAddress(contract)
                .capabilities("STANDARD_RETURN")
                .riskLevel("LOW")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any())).thenReturn(config);
        when(erc20RpcClient.preCheckTransfer(contract, from, to, amount))
                .thenReturn(CallResult.success());
        when(walletService.sendERC20TransferInternal(from, to, contract, amount))
                .thenReturn(expectedTxHash);

        TransactionRecord result = sender.sendERC20Transfer(from, to, contract, amount);

        assertEquals(expectedTxHash, result.getTxHash());
        verify(tokenConfigMapper).selectOne(any());
        verify(erc20RpcClient).preCheckTransfer(contract, from, to, amount);
        verify(walletService).sendERC20TransferInternal(from, to, contract, amount);
        verifyNoMoreInteractions(walletService);
    }

    @Test
    void sendERC20Transfer_rebasingToken_admissionRejected() {
        String from = "0xhotwallet";
        String to = "0xuser";
        String contract = "0xrebasing";
        BigInteger amount = BigInteger.valueOf(500000);

        TokenConfig config = TokenConfig.builder()
                .contractAddress(contract)
                .capabilities("REBASING")
                .riskLevel("HIGH")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any())).thenReturn(config);

        assertThrows(TokenAdmissionRejectedException.class,
                () -> sender.sendERC20Transfer(from, to, contract, amount));

        verifyNoInteractions(erc20RpcClient);
        verify(walletService, never()).sendERC20TransferInternal(any(), any(), any(), any());
    }

    @Test
    void sendERC20Transfer_preCheckReverted_throwsPreCheckFailed() {
        String from = "0xhotwallet";
        String to = "0xuser";
        String contract = "0xtoken";
        BigInteger amount = BigInteger.valueOf(2000000);

        TokenConfig config = TokenConfig.builder()
                .contractAddress(contract)
                .capabilities("STANDARD_RETURN")
                .riskLevel("LOW")
                .enabled(1)
                .build();
        when(tokenConfigMapper.selectOne(any())).thenReturn(config);
        when(erc20RpcClient.preCheckTransfer(contract, from, to, amount))
                .thenReturn(CallResult.reverted());

        TransferPreCheckFailedException ex = assertThrows(
                TransferPreCheckFailedException.class,
                () -> sender.sendERC20Transfer(from, to, contract, amount));

        assertEquals(contract, ex.getContractAddress());
        assertFalse(ex.getCallResult().isSuccess());
        verify(walletService, never()).sendERC20TransferInternal(any(), any(), any(), any());
    }

    @Test
    void sendERC20Transfer_unknownToken_admissionRejected() {
        String from = "0xhotwallet";
        String to = "0xuser";
        String contract = "0xunknown";
        BigInteger amount = BigInteger.valueOf(100000);

        when(tokenConfigMapper.selectOne(any())).thenReturn(null);

        assertThrows(TokenAdmissionRejectedException.class,
                () -> sender.sendERC20Transfer(from, to, contract, amount));

        verifyNoInteractions(erc20RpcClient);
        verify(walletService, never()).sendERC20TransferInternal(any(), any(), any(), any());
    }
}
