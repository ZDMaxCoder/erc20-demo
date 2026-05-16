package com.erc20.platform.blockchain.reconcile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.AccountBalanceMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChainReconcileJobTest {

    private static final String HOT_WALLET = "0xhot1234567890abcdef1234567890abcdef12345678";
    private static final String CONTRACT = "0xtoken1234567890abcdef1234567890abcdef123456";

    @Mock
    private SafeERC20Caller safeERC20Caller;
    @Mock
    private TokenConfigMapper tokenConfigMapper;
    @Mock
    private WalletConfigMapper walletConfigMapper;
    @Mock
    private AccountBalanceMapper accountBalanceMapper;
    @Mock
    private AlertService alertService;

    private ChainReconcileJob chainReconcileJob;

    @BeforeEach
    void setUp() {
        chainReconcileJob = new ChainReconcileJob(safeERC20Caller, tokenConfigMapper,
                walletConfigMapper, accountBalanceMapper, alertService);
    }

    private TokenConfig buildToken(Long id, String symbol, String contract, int decimals, int exponent) {
        return TokenConfig.builder()
                .id(id)
                .tokenSymbol(symbol)
                .contractAddress(contract)
                .decimals(decimals)
                .amountExponent(exponent)
                .enabled(1)
                .tokenType("STANDARD")
                .build();
    }

    private WalletConfig buildHotWallet() {
        return WalletConfig.builder()
                .id(1L)
                .address(HOT_WALLET)
                .walletType("HOT")
                .enabled(1)
                .build();
    }

    // 12.1: hot wallet balance matches → no alert
    @Test
    void reconcile_balanceMatches_noAlert() {
        TokenConfig token = buildToken(1L, "USDT", CONTRACT, 6, 6);
        doReturn(Collections.singletonList(token)).when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(buildHotWallet()).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        // On-chain: 1000 (in token smallest unit, 6 decimals → 1000 in minUnit with exponent 6)
        doReturn(BigInteger.valueOf(1000)).when(safeERC20Caller).safeBalanceOf(CONTRACT, HOT_WALLET);

        // Platform: user1 has 600 available + 400 frozen = 1000
        AccountBalance balance = AccountBalance.builder()
                .tokenId(1L)
                .availableBalance(600L)
                .frozenBalance(400L)
                .build();
        doReturn(Collections.singletonList(balance)).when(accountBalanceMapper).selectList(any(LambdaQueryWrapper.class));

        chainReconcileJob.reconcile();

        verify(alertService, never()).alert(eq("CHAIN_BALANCE_MISMATCH"), any(AlertLevel.class), anyString());
    }

    // 12.2: hot wallet balance diverges → CHAIN_BALANCE_MISMATCH alert
    @Test
    void reconcile_balanceDiverges_alertRaised() {
        TokenConfig token = buildToken(1L, "USDT", CONTRACT, 6, 6);
        doReturn(Collections.singletonList(token)).when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(buildHotWallet()).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        // On-chain: 500
        doReturn(BigInteger.valueOf(500)).when(safeERC20Caller).safeBalanceOf(CONTRACT, HOT_WALLET);

        // Platform: 1000
        AccountBalance balance = AccountBalance.builder()
                .tokenId(1L)
                .availableBalance(600L)
                .frozenBalance(400L)
                .build();
        doReturn(Collections.singletonList(balance)).when(accountBalanceMapper).selectList(any(LambdaQueryWrapper.class));

        chainReconcileJob.reconcile();

        verify(alertService).alert(eq("CHAIN_BALANCE_MISMATCH"), eq(AlertLevel.CRITICAL), contains("USDT"));
    }

    // 12.3: balanceOf RPC fails → WARN alert, continues with other tokens
    @Test
    void reconcile_rpcFails_warnAlertAndContinues() {
        TokenConfig token1 = buildToken(1L, "USDT", CONTRACT, 6, 6);
        TokenConfig token2 = buildToken(2L, "DAI", "0xdai", 6, 6);
        doReturn(Arrays.asList(token1, token2)).when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(buildHotWallet()).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        // Token1 RPC fails
        doThrow(new RuntimeException("connection timeout"))
                .when(safeERC20Caller).safeBalanceOf(CONTRACT, HOT_WALLET);

        // Token2 succeeds and matches
        doReturn(BigInteger.valueOf(5000L)).when(safeERC20Caller).safeBalanceOf("0xdai", HOT_WALLET);
        AccountBalance balance2 = AccountBalance.builder()
                .tokenId(2L)
                .availableBalance(3000L)
                .frozenBalance(2000L)
                .build();
        doReturn(Collections.singletonList(balance2)).when(accountBalanceMapper).selectList(any(LambdaQueryWrapper.class));

        chainReconcileJob.reconcile();

        // WARN alert for RPC failure on token1
        verify(alertService).alert(eq("CHAIN_RECONCILE_ERROR"), eq(AlertLevel.WARN), contains("USDT"));
        // No CRITICAL alert because token2 matched
        verify(alertService, never()).alert(eq("CHAIN_BALANCE_MISMATCH"), any(AlertLevel.class), anyString());
    }
}
