package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.gas.GasPriority;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.service.gateway.WithdrawTransactionSender;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

@Component
public class WithdrawTransactionSenderImpl implements WithdrawTransactionSender {

    private final WalletService walletService;

    public WithdrawTransactionSenderImpl(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public TransactionRecord sendERC20Transfer(String from, String to, String contract,
                                               BigInteger amount) {
        return walletService.sendERC20Transfer(from, to, contract, amount, GasPriority.MEDIUM);
    }

    @Override
    public TxStatus queryTransactionStatus(String txHash) {
        return walletService.queryTransactionStatus(txHash);
    }
}
