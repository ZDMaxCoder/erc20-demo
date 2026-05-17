package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

@Component
public class BalanceDiffChecker {

    private final SafeERC20Caller safeERC20Caller;

    public BalanceDiffChecker(SafeERC20Caller safeERC20Caller) {
        this.safeERC20Caller = safeERC20Caller;
    }

    public BigInteger queryBalance(String contract, String address) {
        return safeERC20Caller.safeBalanceOf(contract, address);
    }

    public BigInteger computeDiff(BigInteger balanceBefore, BigInteger balanceAfter) {
        if (balanceBefore == null || balanceAfter == null) {
            return null;
        }
        return balanceAfter.subtract(balanceBefore);
    }
}
