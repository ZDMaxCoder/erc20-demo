package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.gas.GasEstimator;
import com.erc20.platform.blockchain.gas.GasPrice;
import com.erc20.platform.blockchain.gas.GasPriceCache;
import com.erc20.platform.blockchain.gas.GasPriority;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.service.gateway.CollectionTransactionSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;

@Slf4j
@Component
public class CollectionTransactionSenderImpl implements CollectionTransactionSender {

    private final WalletService walletService;
    private final SafeERC20Caller safeERC20Caller;
    private final GasEstimator gasEstimator;
    private final GasPriceCache gasPriceCache;
    private final Web3j web3j;

    public CollectionTransactionSenderImpl(WalletService walletService,
                                           SafeERC20Caller safeERC20Caller,
                                           GasEstimator gasEstimator,
                                           GasPriceCache gasPriceCache,
                                           Web3j web3j) {
        this.walletService = walletService;
        this.safeERC20Caller = safeERC20Caller;
        this.gasEstimator = gasEstimator;
        this.gasPriceCache = gasPriceCache;
        this.web3j = web3j;
    }

    @Override
    public BigInteger getEthBalance(String address) {
        try {
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send().getBalance();
        } catch (Exception e) {
            log.error("Failed to get ETH balance for {}", address, e);
            throw new RuntimeException("Failed to get ETH balance", e);
        }
    }

    @Override
    public BigInteger getERC20Balance(String contract, String owner) {
        return safeERC20Caller.safeBalanceOf(contract, owner);
    }

    @Override
    public BigInteger estimateGasCost(String contract, String from, String to, BigInteger amount) {
        BigInteger gasLimit = gasEstimator.estimateERC20Transfer(contract, from, to, amount);
        GasPrice gasPrice = gasPriceCache.getCachedGasPrice(GasPriority.MEDIUM);
        BigInteger price = gasPrice.isEip1559() ? gasPrice.getMaxFeePerGas() : gasPrice.getGasPrice();
        return gasLimit.multiply(price);
    }

    @Override
    public TransactionRecord sendEthTransfer(String from, String to, BigInteger amountWei) {
        return walletService.sendEthTransfer(from, to, amountWei, GasPriority.MEDIUM);
    }

    @Override
    public TransactionRecord sendERC20Transfer(String from, String to, String contract, BigInteger amount) {
        return walletService.sendERC20Transfer(from, to, contract, amount, GasPriority.MEDIUM);
    }
}
