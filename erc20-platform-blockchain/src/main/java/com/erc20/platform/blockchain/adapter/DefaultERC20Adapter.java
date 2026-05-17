package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.wallet.SafeTransferExecutor;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Service
public class DefaultERC20Adapter implements ERC20Adapter {

    private final SafeERC20Caller safeERC20Caller;
    private final TokenAdmissionGateway tokenAdmissionGateway;
    private final SafeTransferExecutor safeTransferExecutor;
    private final TransferConfirmer transferConfirmer;
    private final TokenRiskProfileRegistry tokenRiskProfileRegistry;
    private final TokenMetadataCache tokenMetadataCache;

    public DefaultERC20Adapter(SafeERC20Caller safeERC20Caller,
                               TokenAdmissionGateway tokenAdmissionGateway,
                               SafeTransferExecutor safeTransferExecutor,
                               TransferConfirmer transferConfirmer,
                               TokenRiskProfileRegistry tokenRiskProfileRegistry,
                               TokenMetadataCache tokenMetadataCache) {
        this.safeERC20Caller = safeERC20Caller;
        this.tokenAdmissionGateway = tokenAdmissionGateway;
        this.safeTransferExecutor = safeTransferExecutor;
        this.transferConfirmer = transferConfirmer;
        this.tokenRiskProfileRegistry = tokenRiskProfileRegistry;
        this.tokenMetadataCache = tokenMetadataCache;
    }

    @Override
    public BigInteger balanceOf(String contract, String owner) {
        return safeERC20Caller.safeBalanceOf(contract, owner);
    }

    @Override
    public int decimals(String contract) {
        return tokenMetadataCache.getDecimals(contract);
    }

    @Override
    public String symbol(String contract) {
        return tokenMetadataCache.getSymbol(contract);
    }

    @Override
    public String name(String contract) {
        return tokenMetadataCache.getName(contract);
    }

    @Override
    public BigInteger allowance(String contract, String owner, String spender) {
        return safeERC20Caller.safeAllowance(contract, owner, spender);
    }

    @Override
    public String safeTransfer(String fromAddress, String contract, String toAddress, BigInteger amount) {
        tokenAdmissionGateway.checkAdmission(contract, "transfer");
        return safeTransferExecutor.executeTransfer(fromAddress, contract, toAddress, amount);
    }

    @Override
    public String safeApprove(String ownerAddress, String contract, String spender, BigInteger amount) {
        tokenAdmissionGateway.checkAdmission(contract, "approve");
        TokenRiskProfile profile = tokenRiskProfileRegistry.getProfile(contract);
        if (profile.requiresApproveReset() && amount.compareTo(BigInteger.ZERO) > 0) {
            safeTransferExecutor.executeApprove(ownerAddress, contract, spender, BigInteger.ZERO);
        }
        return safeTransferExecutor.executeApprove(ownerAddress, contract, spender, amount);
    }

    @Override
    public TransferResult confirmTransfer(String txHash, String contract, BigInteger expectedAmount, String toAddress) {
        return transferConfirmer.confirm(txHash, contract, expectedAmount, toAddress);
    }

    @Override
    public TokenRiskProfile getTokenProfile(String contract) {
        return tokenRiskProfileRegistry.getProfile(contract);
    }

    @Override
    public boolean isTokenAdmitted(String contract) {
        return tokenAdmissionGateway.isAdmitted(contract);
    }
}
