package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.WalletType;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.service.gateway.CollectionTransactionSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;

@Slf4j
@Service
public class WalletTransferService {

    private static final BigInteger ETH_LOW_THRESHOLD = BigInteger.valueOf(100_000_000_000_000_000L);
    private static final BigInteger ETH_CRITICAL_THRESHOLD = BigInteger.valueOf(10_000_000_000_000_000L);

    private final WalletConfigMapper walletConfigMapper;
    private final TokenConfigMapper tokenConfigMapper;
    private final AlertService alertService;
    private final CollectionTransactionSender transactionSender;

    public WalletTransferService(WalletConfigMapper walletConfigMapper,
                                 TokenConfigMapper tokenConfigMapper,
                                 AlertService alertService,
                                 CollectionTransactionSender transactionSender) {
        this.walletConfigMapper = walletConfigMapper;
        this.tokenConfigMapper = tokenConfigMapper;
        this.alertService = alertService;
        this.transactionSender = transactionSender;
    }

    @Scheduled(fixedDelay = 300000)
    public void checkHotWalletBalance() {
        WalletConfig hotWallet = walletConfigMapper.selectOne(
                new LambdaQueryWrapper<WalletConfig>()
                        .eq(WalletConfig::getWalletType, WalletType.HOT.getCode())
                        .eq(WalletConfig::getEnabled, 1));
        if (hotWallet == null) {
            return;
        }

        checkEthBalance(hotWallet);
        checkTokenBalances(hotWallet);
    }

    private void checkEthBalance(WalletConfig hotWallet) {
        BigInteger ethBalance = transactionSender.getEthBalance(hotWallet.getAddress());
        if (ethBalance.compareTo(ETH_CRITICAL_THRESHOLD) < 0) {
            log.error("Hot wallet {} ETH balance critically low: {}", hotWallet.getAddress(), ethBalance);
            alertService.alert("HOT_WALLET_ETH_LOW", AlertLevel.CRITICAL,
                    String.format("address=%s, balance=%s wei", hotWallet.getAddress(), ethBalance));
        } else if (ethBalance.compareTo(ETH_LOW_THRESHOLD) < 0) {
            log.warn("Hot wallet {} ETH balance low: {}", hotWallet.getAddress(), ethBalance);
            alertService.alert("HOT_WALLET_ETH_LOW", AlertLevel.WARN,
                    String.format("address=%s, balance=%s wei", hotWallet.getAddress(), ethBalance));
        }
    }

    private void checkTokenBalances(WalletConfig hotWallet) {
        List<TokenConfig> tokens = tokenConfigMapper.selectList(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getEnabled, 1));

        for (TokenConfig token : tokens) {
            BigInteger balance = transactionSender.getERC20Balance(
                    token.getContractAddress(), hotWallet.getAddress());
            log.info("Hot wallet {} token {} balance: {}", hotWallet.getAddress(),
                    token.getTokenSymbol(), balance);
        }
    }

}
