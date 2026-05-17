package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.ERC20Adapter;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.service.gateway.WithdrawTransactionSender;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

@Component
public class WithdrawTransactionSenderImpl implements WithdrawTransactionSender {

    private final ERC20Adapter erc20Adapter;
    private final WalletService walletService;

    public WithdrawTransactionSenderImpl(ERC20Adapter erc20Adapter, WalletService walletService) {
        this.erc20Adapter = erc20Adapter;
        this.walletService = walletService;
    }

    @Override
    public TransactionRecord sendERC20Transfer(String from, String to, String contract,
                                               BigInteger amount) {
        String txHash = erc20Adapter.safeTransfer(from, contract, to, amount);
        return TransactionRecord.builder().txHash(txHash).build();
    }

    @Override
    public TxStatus queryTransactionStatus(String txHash) {
        return walletService.queryTransactionStatus(txHash);
    }
}
