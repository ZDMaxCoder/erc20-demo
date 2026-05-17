package com.erc20.platform.blockchain.wallet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.adapter.model.CallResult;
import com.erc20.platform.blockchain.adapter.rpc.ERC20RpcClient;
import com.erc20.platform.blockchain.erc20.ChainCallException;
import com.erc20.platform.blockchain.gas.GasEstimator;
import com.erc20.platform.blockchain.gas.GasPrice;
import com.erc20.platform.blockchain.gas.GasPriceCache;
import com.erc20.platform.blockchain.gas.GasPriority;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.exception.ContractRevertException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.dal.mapper.TransactionRecordMapper;
import com.erc20.platform.domain.entity.TransactionRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class WalletService {

    private static final BigInteger ETH_TRANSFER_GAS_LIMIT = BigInteger.valueOf(21000);

    private final NonceManager nonceManager;
    private final GasPriceCache gasPriceCache;
    private final GasEstimator gasEstimator;
    private final TransactionBuilder transactionBuilder;
    private final TransactionSigner transactionSigner;
    private final TransactionBroadcaster transactionBroadcaster;
    private final TransactionRecordMapper transactionRecordMapper;
    private final Web3j web3j;
    private final ERC20RpcClient erc20RpcClient;
    private final int chainId;

    public WalletService(NonceManager nonceManager,
                         GasPriceCache gasPriceCache,
                         GasEstimator gasEstimator,
                         TransactionBuilder transactionBuilder,
                         TransactionSigner transactionSigner,
                         TransactionBroadcaster transactionBroadcaster,
                         TransactionRecordMapper transactionRecordMapper,
                         Web3j web3j,
                         ERC20RpcClient erc20RpcClient,
                         @Value("${blockchain.sync.chain-id:1}") int chainId) {
        this.nonceManager = nonceManager;
        this.gasPriceCache = gasPriceCache;
        this.gasEstimator = gasEstimator;
        this.transactionBuilder = transactionBuilder;
        this.transactionSigner = transactionSigner;
        this.transactionBroadcaster = transactionBroadcaster;
        this.transactionRecordMapper = transactionRecordMapper;
        this.web3j = web3j;
        this.erc20RpcClient = erc20RpcClient;
        this.chainId = chainId;
    }

    public TransactionRecord sendERC20Transfer(String from, String to, String contract,
                                               BigInteger amount, GasPriority priority) {
        CallResult preCheck;
        try {
            preCheck = erc20RpcClient.preCheckTransfer(contract, from, to, amount);
        } catch (ChainCallException e) {
            throw new BizException(ErrorCode.CHAIN_ERROR, e.getMessage());
        }
        if (!preCheck.isSuccess()) {
            if (preCheck.isDangerousFalse()) {
                throw new BizException(ErrorCode.CHAIN_ERROR, "Transfer would return false");
            }
            throw new BizException(ErrorCode.CHAIN_ERROR, "Transfer pre-check failed: " + preCheck.getOutcome());
        }

        return doSendERC20Transfer(from, to, contract, amount, priority);
    }

    String sendApproveInternal(String owner, String contract, String spender, BigInteger amount) {
        long nonce = nonceManager.allocateNonce(chainId, owner);
        try {
            GasPrice gasPrice = gasPriceCache.getCachedGasPrice(GasPriority.MEDIUM);
            BigInteger gasLimit = gasEstimator.estimateERC20Approve(contract, owner, spender, amount);

            RawTransaction rawTx = transactionBuilder.buildERC20Approve(
                    chainId, nonce, gasPrice, gasLimit, contract, spender, amount);
            String signedTx = transactionSigner.sign(rawTx, chainId);

            BroadcastResult broadcastResult = transactionBroadcaster.broadcast(signedTx);

            if (!broadcastResult.isSuccess()
                    && broadcastResult.getErrorType() == BroadcastErrorType.NONCE_TOO_LOW) {
                log.warn("NONCE_TOO_LOW for address {}, resetting and retrying once", owner);
                nonceManager.resetNonce(chainId, owner);
                nonce = nonceManager.allocateNonce(chainId, owner);

                rawTx = transactionBuilder.buildERC20Approve(
                        chainId, nonce, gasPrice, gasLimit, contract, spender, amount);
                signedTx = transactionSigner.sign(rawTx, chainId);
                broadcastResult = transactionBroadcaster.broadcast(signedTx);

                if (!broadcastResult.isSuccess()) {
                    throw new BizException(ErrorCode.BROADCAST_FAILED,
                            broadcastResult.getErrorMessage());
                }
            } else if (!broadcastResult.isSuccess()) {
                throw new BizException(ErrorCode.BROADCAST_FAILED,
                        broadcastResult.getErrorMessage());
            }

            TransactionRecord record = TransactionRecord.builder()
                    .txHash(broadcastResult.getTxHash())
                    .chainId(chainId)
                    .fromAddress(owner)
                    .toAddress(spender)
                    .txType("ERC20_APPROVE")
                    .nonce(nonce)
                    .gasPrice(gasPrice.isEip1559()
                            ? gasPrice.getMaxFeePerGas().longValue()
                            : gasPrice.getGasPrice().longValue())
                    .gasLimit(gasLimit.longValue())
                    .status(TxStatus.PENDING.getCode())
                    .rawTx(signedTx)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            transactionRecordMapper.insert(record);
            return record.getTxHash();
        } catch (BizException e) {
            nonceManager.releaseNonce(chainId, owner, nonce);
            throw e;
        } catch (Exception e) {
            nonceManager.releaseNonce(chainId, owner, nonce);
            throw new BizException(ErrorCode.CHAIN_ERROR, e.getMessage());
        }
    }

    String sendERC20TransferInternal(String from, String to, String contract, BigInteger amount) {
        TransactionRecord record = doSendERC20Transfer(from, to, contract, amount, GasPriority.MEDIUM);
        return record.getTxHash();
    }

    private TransactionRecord doSendERC20Transfer(String from, String to, String contract,
                                                   BigInteger amount, GasPriority priority) {
        long nonce = nonceManager.allocateNonce(chainId, from);
        try {
            GasPrice gasPrice = gasPriceCache.getCachedGasPrice(priority);
            BigInteger gasLimit = gasEstimator.estimateERC20Transfer(contract, from, to, amount);

            RawTransaction rawTx = transactionBuilder.buildERC20Transfer(
                    chainId, nonce, gasPrice, gasLimit, contract, to, amount);
            String signedTx = transactionSigner.sign(rawTx, chainId);

            BroadcastResult broadcastResult = transactionBroadcaster.broadcast(signedTx);

            if (!broadcastResult.isSuccess()
                    && broadcastResult.getErrorType() == BroadcastErrorType.NONCE_TOO_LOW) {
                log.warn("NONCE_TOO_LOW for address {}, resetting and retrying once", from);
                nonceManager.resetNonce(chainId, from);
                nonce = nonceManager.allocateNonce(chainId, from);

                rawTx = transactionBuilder.buildERC20Transfer(
                        chainId, nonce, gasPrice, gasLimit, contract, to, amount);
                signedTx = transactionSigner.sign(rawTx, chainId);
                broadcastResult = transactionBroadcaster.broadcast(signedTx);

                if (!broadcastResult.isSuccess()) {
                    throw new BizException(ErrorCode.BROADCAST_FAILED,
                            broadcastResult.getErrorMessage());
                }
            } else if (!broadcastResult.isSuccess()) {
                throw new BizException(ErrorCode.BROADCAST_FAILED,
                        broadcastResult.getErrorMessage());
            }

            TransactionRecord record = TransactionRecord.builder()
                    .txHash(broadcastResult.getTxHash())
                    .chainId(chainId)
                    .fromAddress(from)
                    .toAddress(to)
                    .txType("ERC20_TRANSFER")
                    .nonce(nonce)
                    .gasPrice(gasPrice.isEip1559()
                            ? gasPrice.getMaxFeePerGas().longValue()
                            : gasPrice.getGasPrice().longValue())
                    .gasLimit(gasLimit.longValue())
                    .status(TxStatus.PENDING.getCode())
                    .rawTx(signedTx)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            transactionRecordMapper.insert(record);
            return record;
        } catch (ContractRevertException e) {
            nonceManager.releaseNonce(chainId, from, nonce);
            throw new BizException(ErrorCode.CHAIN_ERROR, "Contract call would revert: " + e.getMessage());
        } catch (BizException e) {
            nonceManager.releaseNonce(chainId, from, nonce);
            throw e;
        } catch (Exception e) {
            nonceManager.releaseNonce(chainId, from, nonce);
            throw new BizException(ErrorCode.CHAIN_ERROR, e.getMessage());
        }
    }

    public TransactionRecord sendEthTransfer(String from, String to, BigInteger amountWei,
                                             GasPriority priority) {
        long nonce = nonceManager.allocateNonce(chainId, from);
        try {
            GasPrice gasPrice = gasPriceCache.getCachedGasPrice(priority);

            RawTransaction rawTx = transactionBuilder.buildEthTransfer(
                    chainId, nonce, gasPrice, ETH_TRANSFER_GAS_LIMIT, to, amountWei);
            String signedTx = transactionSigner.sign(rawTx, chainId);

            BroadcastResult broadcastResult = transactionBroadcaster.broadcast(signedTx);

            if (!broadcastResult.isSuccess()
                    && broadcastResult.getErrorType() == BroadcastErrorType.NONCE_TOO_LOW) {
                log.warn("NONCE_TOO_LOW for address {}, resetting and retrying once", from);
                nonceManager.resetNonce(chainId, from);
                nonce = nonceManager.allocateNonce(chainId, from);

                rawTx = transactionBuilder.buildEthTransfer(
                        chainId, nonce, gasPrice, ETH_TRANSFER_GAS_LIMIT, to, amountWei);
                signedTx = transactionSigner.sign(rawTx, chainId);
                broadcastResult = transactionBroadcaster.broadcast(signedTx);

                if (!broadcastResult.isSuccess()) {
                    throw new BizException(ErrorCode.BROADCAST_FAILED,
                            broadcastResult.getErrorMessage());
                }
            } else if (!broadcastResult.isSuccess()) {
                throw new BizException(ErrorCode.BROADCAST_FAILED,
                        broadcastResult.getErrorMessage());
            }

            TransactionRecord record = TransactionRecord.builder()
                    .txHash(broadcastResult.getTxHash())
                    .chainId(chainId)
                    .fromAddress(from)
                    .toAddress(to)
                    .txType("ETH_TRANSFER")
                    .nonce(nonce)
                    .gasPrice(gasPrice.isEip1559()
                            ? gasPrice.getMaxFeePerGas().longValue()
                            : gasPrice.getGasPrice().longValue())
                    .gasLimit(ETH_TRANSFER_GAS_LIMIT.longValue())
                    .status(TxStatus.PENDING.getCode())
                    .rawTx(signedTx)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            transactionRecordMapper.insert(record);
            return record;
        } catch (BizException e) {
            nonceManager.releaseNonce(chainId, from, nonce);
            throw e;
        } catch (Exception e) {
            nonceManager.releaseNonce(chainId, from, nonce);
            throw new BizException(ErrorCode.CHAIN_ERROR, e.getMessage());
        }
    }

    public TransactionRecord replaceTransaction(String txHash, boolean cancel) {
        TransactionRecord original = transactionRecordMapper.selectOne(
                new LambdaQueryWrapper<TransactionRecord>()
                        .eq(TransactionRecord::getTxHash, txHash));
        if (original == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Transaction not found: " + txHash);
        }

        GasPrice originalGasPrice = GasPrice.builder()
                .eip1559(false)
                .gasPrice(BigInteger.valueOf(original.getGasPrice()))
                .build();
        GasPrice replacementGasPrice = gasPriceCache.getReplacementGasPrice(originalGasPrice);

        RawTransaction rawTx;
        if (cancel) {
            rawTx = transactionBuilder.buildEthTransfer(
                    chainId, original.getNonce(), replacementGasPrice,
                    BigInteger.valueOf(original.getGasLimit()),
                    original.getToAddress(), BigInteger.ZERO);
        } else {
            rawTx = transactionBuilder.buildEthTransfer(
                    chainId, original.getNonce(), replacementGasPrice,
                    BigInteger.valueOf(original.getGasLimit()),
                    original.getToAddress(), BigInteger.ZERO);
        }

        String signedTx = transactionSigner.sign(rawTx, chainId);
        BroadcastResult broadcastResult = transactionBroadcaster.broadcast(signedTx);
        if (!broadcastResult.isSuccess()) {
            throw new BizException(ErrorCode.BROADCAST_FAILED, broadcastResult.getErrorMessage());
        }

        TransactionRecord replacementRecord = TransactionRecord.builder()
                .txHash(broadcastResult.getTxHash())
                .chainId(chainId)
                .fromAddress(original.getFromAddress())
                .toAddress(original.getToAddress())
                .txType(original.getTxType())
                .nonce(original.getNonce())
                .gasPrice(replacementGasPrice.isEip1559()
                        ? replacementGasPrice.getMaxFeePerGas().longValue()
                        : replacementGasPrice.getGasPrice().longValue())
                .gasLimit(original.getGasLimit())
                .status(TxStatus.PENDING.getCode())
                .rawTx(signedTx)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        transactionRecordMapper.insert(replacementRecord);

        original.setStatus(TxStatus.REPLACED.getCode());
        original.setReplacedByTxHash(broadcastResult.getTxHash());
        original.setUpdatedAt(new Date());
        transactionRecordMapper.updateById(original);

        return replacementRecord;
    }

    public TxStatus queryTransactionStatus(String txHash) {
        try {
            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
            Optional<TransactionReceipt> receipt = receiptResponse.getTransactionReceipt();

            if (!receipt.isPresent()) {
                return TxStatus.PENDING;
            }

            String status = receipt.get().getStatus();
            if ("0x1".equals(status)) {
                return TxStatus.CONFIRMED;
            }
            return TxStatus.FAILED;
        } catch (Exception e) {
            log.error("Failed to query transaction status for {}", txHash, e);
            throw new BizException(ErrorCode.CHAIN_ERROR, e.getMessage());
        }
    }
}
