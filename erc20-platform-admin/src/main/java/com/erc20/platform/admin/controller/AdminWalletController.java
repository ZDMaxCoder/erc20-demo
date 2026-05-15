package com.erc20.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.admin.vo.request.NonceResetRequest;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.service.CollectionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "Admin - Wallet")
@RestController
@RequestMapping("/api/admin/v1")
public class AdminWalletController {

    private final WalletConfigMapper walletConfigMapper;
    private final CollectionService collectionService;
    private final NonceManager nonceManager;

    public AdminWalletController(WalletConfigMapper walletConfigMapper,
                                 CollectionService collectionService,
                                 NonceManager nonceManager) {
        this.walletConfigMapper = walletConfigMapper;
        this.collectionService = collectionService;
        this.nonceManager = nonceManager;
    }

    @ApiOperation("Get wallet balances")
    @GetMapping("/wallet/balances")
    public Result<List<WalletConfig>> getWalletBalances() {
        List<WalletConfig> wallets = walletConfigMapper.selectList(
                new LambdaQueryWrapper<WalletConfig>()
                        .eq(WalletConfig::getEnabled, 1));
        return Result.success(wallets);
    }

    @ApiOperation("Trigger batch collection")
    @PostMapping("/collection/trigger")
    public Result<Void> triggerCollection() {
        collectionService.batchCollection();
        return Result.success();
    }

    @ApiOperation("Reset wallet nonce")
    @PostMapping("/nonce/reset")
    public Result<Void> resetNonce(@Valid @RequestBody NonceResetRequest request) {
        nonceManager.resetNonce(request.getChainId(), request.getWalletAddress());
        return Result.success();
    }
}
