package com.erc20.platform.blockchain.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.adapter.ERC20Adapter;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.util.AmountUtil;
import com.erc20.platform.dal.mapper.AccountBalanceMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Component
public class ChainReconcileJob {

    private final ERC20Adapter erc20Adapter;
    private final TokenConfigMapper tokenConfigMapper;
    private final WalletConfigMapper walletConfigMapper;
    private final AccountBalanceMapper accountBalanceMapper;
    private final AlertService alertService;

    public ChainReconcileJob(ERC20Adapter erc20Adapter,
                             TokenConfigMapper tokenConfigMapper,
                             WalletConfigMapper walletConfigMapper,
                             AccountBalanceMapper accountBalanceMapper,
                             AlertService alertService) {
        this.erc20Adapter = erc20Adapter;
        this.tokenConfigMapper = tokenConfigMapper;
        this.walletConfigMapper = walletConfigMapper;
        this.accountBalanceMapper = accountBalanceMapper;
        this.alertService = alertService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcile() {
        log.info("Starting chain balance reconciliation");

        List<TokenConfig> tokens = tokenConfigMapper.selectList(
                new LambdaQueryWrapper<TokenConfig>());

        WalletConfig hotWallet = walletConfigMapper.selectOne(
                new LambdaQueryWrapper<WalletConfig>()
                        .eq(WalletConfig::getWalletType, "HOT")
                        .eq(WalletConfig::getEnabled, 1));

        if (hotWallet == null) {
            log.warn("No enabled hot wallet found, skipping reconciliation");
            return;
        }

        for (TokenConfig token : tokens) {
            try {
                reconcileToken(token, hotWallet);
            } catch (Exception e) {
                log.error("Chain reconciliation failed for token {}: {}", token.getTokenSymbol(), e.getMessage());
                alertService.alert("CHAIN_RECONCILE_ERROR", AlertLevel.WARN,
                        String.format("balanceOf failed for token %s: %s", token.getTokenSymbol(), e.getMessage()));
            }
        }

        log.info("Chain balance reconciliation completed");
    }

    private void reconcileToken(TokenConfig token, WalletConfig hotWallet) {
        BigInteger onChainBalance = erc20Adapter.balanceOf(
                token.getContractAddress(), hotWallet.getAddress());

        long onChainMinUnit = AmountUtil.fromChainAmount(
                onChainBalance, token.getDecimals(), token.getAmountExponent());

        List<AccountBalance> balances = accountBalanceMapper.selectList(
                new LambdaQueryWrapper<AccountBalance>().eq(AccountBalance::getTokenId, token.getId()));

        long expectedBalance = 0;
        for (AccountBalance b : balances) {
            expectedBalance += b.getAvailableBalance() + b.getFrozenBalance();
        }

        if (onChainMinUnit != expectedBalance) {
            long diff = onChainMinUnit - expectedBalance;
            alertService.alert("CHAIN_BALANCE_MISMATCH", AlertLevel.CRITICAL,
                    String.format("token=%s, wallet=%s, expected=%d, actual=%d, diff=%d",
                            token.getTokenSymbol(), hotWallet.getAddress(),
                            expectedBalance, onChainMinUnit, diff));
        }
    }
}
