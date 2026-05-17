package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.blockchain.adapter.model.TransferResult;

import java.math.BigInteger;

public interface ERC20Adapter {

    BigInteger balanceOf(String contract, String owner);

    int decimals(String contract);

    String symbol(String contract);

    String name(String contract);

    BigInteger allowance(String contract, String owner, String spender);

    String safeTransfer(String fromAddress, String contract, String toAddress, BigInteger amount);

    String safeApprove(String ownerAddress, String contract, String spender, BigInteger amount);

    TransferResult confirmTransfer(String txHash, String contract, BigInteger expectedAmount, String toAddress);

    TokenRiskProfile getTokenProfile(String contract);

    boolean isTokenAdmitted(String contract);
}
