package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.adapter.exception.TransferPreCheckFailedException;
import com.erc20.platform.blockchain.adapter.model.CallResult;
import com.erc20.platform.blockchain.adapter.rpc.ERC20RpcClient;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

@Component
public class SafeTransferExecutor {

    private final ERC20RpcClient erc20RpcClient;
    private final WalletService walletService;

    public SafeTransferExecutor(ERC20RpcClient erc20RpcClient, WalletService walletService) {
        this.erc20RpcClient = erc20RpcClient;
        this.walletService = walletService;
    }

    public String executeTransfer(String from, String contract, String to, BigInteger amount) {
        CallResult preCheck = erc20RpcClient.preCheckTransfer(contract, from, to, amount);
        if (!preCheck.isSuccess()) {
            throw new TransferPreCheckFailedException(contract, preCheck);
        }
        return walletService.sendERC20TransferInternal(from, to, contract, amount);
    }

    public String executeApprove(String owner, String contract, String spender, BigInteger amount) {
        CallResult preCheck = erc20RpcClient.preCheckApprove(contract, owner, spender, amount);
        if (!preCheck.isSuccess()) {
            throw new TransferPreCheckFailedException(contract, preCheck);
        }
        return walletService.sendApproveInternal(owner, contract, spender, amount);
    }
}
