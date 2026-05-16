package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.gas.GasEstimator;
import com.erc20.platform.blockchain.gas.GasPrice;
import com.erc20.platform.blockchain.gas.GasPriceCache;
import com.erc20.platform.blockchain.gas.GasPriority;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.exception.ContractRevertException;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private NonceManager nonceManager;
    @Mock private GasPriceCache gasPriceCache;
    @Mock private GasEstimator gasEstimator;
    @Mock private TransactionBuilder transactionBuilder;
    @Mock private TransactionSigner transactionSigner;
    @Mock private TransactionBroadcaster transactionBroadcaster;
    @Mock private TransactionRecordMapper transactionRecordMapper;
    @Mock private Web3j web3j;

    private WalletService walletService;

    private static final int CHAIN_ID = 1;
    private static final String FROM = "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String TO = "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
    private static final String CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final BigInteger AMOUNT = BigInteger.valueOf(1000000);
    private static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String SIGNED_TX = "0xf86c0a85signed";

    @BeforeEach
    void setUp() {
        walletService = new WalletService(
                nonceManager, gasPriceCache, gasEstimator,
                transactionBuilder, transactionSigner, transactionBroadcaster,
                transactionRecordMapper, web3j, CHAIN_ID);
    }

    // --- 6.1 sendERC20Transfer orchestrates full pipeline ---

    @Test
    void sendERC20Transfer_success_orchestratesFullPipeline() {
        long nonce = 5L;
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();
        BigInteger gasLimit = BigInteger.valueOf(80000);
        RawTransaction rawTx = mock(RawTransaction.class);

        doReturn(nonce).when(nonceManager).allocateNonce(CHAIN_ID, FROM);
        doReturn(gasPrice).when(gasPriceCache).getCachedGasPrice(GasPriority.MEDIUM);
        doReturn(gasLimit).when(gasEstimator).estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT);
        doReturn(rawTx).when(transactionBuilder).buildERC20Transfer(CHAIN_ID, nonce, gasPrice, gasLimit, CONTRACT, TO, AMOUNT);
        doReturn(SIGNED_TX).when(transactionSigner).sign(rawTx, CHAIN_ID);
        doReturn(BroadcastResult.success(TX_HASH)).when(transactionBroadcaster).broadcast(SIGNED_TX);
        doReturn(1).when(transactionRecordMapper).insert(any(TransactionRecord.class));

        TransactionRecord result = walletService.sendERC20Transfer(FROM, TO, CONTRACT, AMOUNT, GasPriority.MEDIUM);

        assertNotNull(result);
        assertEquals(TX_HASH, result.getTxHash());
        assertEquals(FROM.toLowerCase(), result.getFromAddress().toLowerCase());
        assertEquals(TO.toLowerCase(), result.getToAddress().toLowerCase());
        assertEquals(nonce, result.getNonce().longValue());
        assertEquals(TxStatus.PENDING.getCode(), result.getStatus());

        verify(nonceManager).allocateNonce(CHAIN_ID, FROM);
        verify(transactionRecordMapper).insert(any(TransactionRecord.class));
        verify(nonceManager, never()).releaseNonce(anyInt(), anyString(), anyLong());
    }

    // --- 6.2 broadcast failure → nonce released, exception thrown ---

    @Test
    void sendERC20Transfer_broadcastFails_releasesNonceAndThrows() {
        long nonce = 5L;
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();
        BigInteger gasLimit = BigInteger.valueOf(80000);
        RawTransaction rawTx = mock(RawTransaction.class);

        doReturn(nonce).when(nonceManager).allocateNonce(CHAIN_ID, FROM);
        doReturn(gasPrice).when(gasPriceCache).getCachedGasPrice(GasPriority.MEDIUM);
        doReturn(gasLimit).when(gasEstimator).estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT);
        doReturn(rawTx).when(transactionBuilder).buildERC20Transfer(CHAIN_ID, nonce, gasPrice, gasLimit, CONTRACT, TO, AMOUNT);
        doReturn(SIGNED_TX).when(transactionSigner).sign(rawTx, CHAIN_ID);
        doReturn(BroadcastResult.error(BroadcastErrorType.INSUFFICIENT_FUNDS, "insufficient funds"))
                .when(transactionBroadcaster).broadcast(SIGNED_TX);

        assertThrows(Exception.class, () ->
                walletService.sendERC20Transfer(FROM, TO, CONTRACT, AMOUNT, GasPriority.MEDIUM));

        verify(nonceManager).releaseNonce(CHAIN_ID, FROM, nonce);
        verify(transactionRecordMapper, never()).insert(argThat(record ->
                TxStatus.CONFIRMED.getCode().equals(record.getStatus())));
    }

    // --- 6.3 replaceTransaction → same nonce, higher gas, original marked REPLACED ---

    @Test
    void replaceTransaction_success_replacesWithHigherGas() {
        TransactionRecord original = TransactionRecord.builder()
                .id(1L)
                .txHash(TX_HASH)
                .fromAddress(FROM)
                .toAddress(TO)
                .chainId(CHAIN_ID)
                .nonce(5L)
                .gasPrice(20_000_000_000L)
                .gasLimit(80000L)
                .txType("ERC20_TRANSFER")
                .status(TxStatus.PENDING.getCode())
                .build();

        GasPrice originalGasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();
        GasPrice replacementGasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(23_000_000_000L))
                .build();

        String newTxHash = "0xnew1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        String newSignedTx = "0xnewsigned";
        RawTransaction rawTx = mock(RawTransaction.class);

        doReturn(original).when(transactionRecordMapper).selectOne(any());
        doReturn(replacementGasPrice).when(gasPriceCache).getReplacementGasPrice(any(GasPrice.class));
        doReturn(rawTx).when(transactionBuilder).buildEthTransfer(
                eq((long) CHAIN_ID), eq(5L), eq(replacementGasPrice), any(BigInteger.class), eq(TO), eq(BigInteger.ZERO));
        doReturn(newSignedTx).when(transactionSigner).sign(rawTx, CHAIN_ID);
        doReturn(BroadcastResult.success(newTxHash)).when(transactionBroadcaster).broadcast(newSignedTx);
        doReturn(1).when(transactionRecordMapper).insert(any(TransactionRecord.class));
        doReturn(1).when(transactionRecordMapper).updateById(any(TransactionRecord.class));

        TransactionRecord result = walletService.replaceTransaction(TX_HASH, true);

        assertNotNull(result);
        assertEquals(newTxHash, result.getTxHash());

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordMapper).updateById(captor.capture());
        TransactionRecord updatedOriginal = captor.getValue();
        assertEquals(TxStatus.REPLACED.getCode(), updatedOriginal.getStatus());
        assertEquals(newTxHash, updatedOriginal.getReplacedByTxHash());
    }

    // --- 7.4 ContractRevertException from gasEstimator → BizException + nonce released ---

    @Test
    void sendERC20Transfer_contractRevert_throwsBizExceptionAndReleasesNonce() {
        long nonce = 5L;
        GasPrice gasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();

        doReturn(nonce).when(nonceManager).allocateNonce(CHAIN_ID, FROM);
        doReturn(gasPrice).when(gasPriceCache).getCachedGasPrice(GasPriority.MEDIUM);
        doThrow(new ContractRevertException("execution reverted: ERC20: transfer amount exceeds balance"))
                .when(gasEstimator).estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT);

        BizException ex = assertThrows(BizException.class, () ->
                walletService.sendERC20Transfer(FROM, TO, CONTRACT, AMOUNT, GasPriority.MEDIUM));

        assertTrue(ex.getMessage().contains("Contract call would revert"));
        verify(nonceManager).releaseNonce(CHAIN_ID, FROM, nonce);
        verify(transactionBroadcaster, never()).broadcast(anyString());
    }
}
