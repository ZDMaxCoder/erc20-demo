package com.erc20.platform.service.gateway;

import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.domain.entity.TransactionRecord;

import java.math.BigInteger;

public interface WithdrawTransactionSender {

    TransactionRecord sendERC20Transfer(String from, String to, String contract,
                                        BigInteger amount);

    TxStatus queryTransactionStatus(String txHash);
}
