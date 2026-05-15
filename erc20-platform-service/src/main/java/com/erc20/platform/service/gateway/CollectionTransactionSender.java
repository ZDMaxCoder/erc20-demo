package com.erc20.platform.service.gateway;

import com.erc20.platform.domain.entity.TransactionRecord;

import java.math.BigInteger;

public interface CollectionTransactionSender {

    BigInteger getEthBalance(String address);

    BigInteger getERC20Balance(String contract, String owner);

    BigInteger estimateGasCost(String contract, String from, String to, BigInteger amount);

    TransactionRecord sendEthTransfer(String from, String to, BigInteger amountWei);

    TransactionRecord sendERC20Transfer(String from, String to, String contract, BigInteger amount);
}
