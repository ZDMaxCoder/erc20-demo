package com.erc20.platform.service;

import com.erc20.platform.dal.mapper.AlertRecordMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.AlertRecord;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.service.gateway.CollectionTransactionSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

@Slf4j
@Service
public class GasSupplyService {

    private final CollectionTransactionSender transactionSender;
    private final WalletConfigMapper walletConfigMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final CollectionProperties properties;

    public GasSupplyService(CollectionTransactionSender transactionSender,
                            WalletConfigMapper walletConfigMapper,
                            AlertRecordMapper alertRecordMapper,
                            CollectionProperties properties) {
        this.transactionSender = transactionSender;
        this.walletConfigMapper = walletConfigMapper;
        this.alertRecordMapper = alertRecordMapper;
        this.properties = properties;
    }

    public BigInteger estimateRequiredGas(String contract, String fromAddress, String toAddress,
                                          BigInteger amount) {
        BigInteger baseCost = transactionSender.estimateGasCost(contract, fromAddress, toAddress, amount);
        return new BigDecimal(baseCost)
                .multiply(properties.getGasBufferMultiplier())
                .toBigInteger();
    }

    public TransactionRecord supplyGas(String hotWallet, String userAddress, String contract,
                                       BigInteger amount) {
        BigInteger requiredGas = estimateRequiredGas(contract, userAddress, hotWallet, amount);
        BigInteger hotBalance = transactionSender.getEthBalance(hotWallet);

        if (hotBalance.compareTo(requiredGas) < 0) {
            log.warn("Hot wallet {} ETH balance {} insufficient for gas supply {}",
                    hotWallet, hotBalance, requiredGas);
            AlertRecord alert = AlertRecord.builder()
                    .alertLevel("WARN")
                    .alertType("HOT_WALLET_INSUFFICIENT_GAS")
                    .title("Hot wallet ETH insufficient for gas supply")
                    .content(String.format("hotWallet=%s, balance=%s, required=%s, userAddress=%s",
                            hotWallet, hotBalance, requiredGas, userAddress))
                    .bizType("COLLECTION")
                    .resolved(0)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            alertRecordMapper.insert(alert);
            return null;
        }

        return transactionSender.sendEthTransfer(hotWallet, userAddress, requiredGas);
    }
}
