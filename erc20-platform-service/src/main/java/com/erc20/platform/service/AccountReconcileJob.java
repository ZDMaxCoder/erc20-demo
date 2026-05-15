package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.AccountBalanceMapper;
import com.erc20.platform.domain.entity.AccountBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AccountReconcileJob {

    private static final int SAMPLE_SIZE = 100;

    private final AccountBalanceMapper accountBalanceMapper;
    private final AccountFlowService accountFlowService;
    private final AlertService alertService;

    public AccountReconcileJob(AccountBalanceMapper accountBalanceMapper,
                               AccountFlowService accountFlowService,
                               AlertService alertService) {
        this.accountBalanceMapper = accountBalanceMapper;
        this.accountFlowService = accountFlowService;
        this.alertService = alertService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void fullScan() {
        log.info("Starting full balance reconciliation scan");
        int page = 1;
        int size = 200;
        long mismatchCount = 0;

        while (true) {
            Page<AccountBalance> pageResult = accountBalanceMapper.selectPage(
                    new Page<>(page, size),
                    new LambdaQueryWrapper<AccountBalance>());

            List<AccountBalance> balances = pageResult.getRecords();
            if (balances.isEmpty()) {
                break;
            }

            for (AccountBalance balance : balances) {
                if (!accountFlowService.verifyBalance(balance.getUserId(), balance.getTokenId())) {
                    mismatchCount++;
                    createAlert(balance);
                }
            }

            if (page >= pageResult.getPages()) {
                break;
            }
            page++;
        }

        log.info("Full reconciliation scan completed. Mismatches found: {}", mismatchCount);
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void sampleScan() {
        log.info("Starting sample balance reconciliation (size={})", SAMPLE_SIZE);
        Page<AccountBalance> pageResult = accountBalanceMapper.selectPage(
                new Page<>(1, SAMPLE_SIZE),
                new LambdaQueryWrapper<AccountBalance>()
                        .orderByDesc(AccountBalance::getUpdatedAt));

        List<AccountBalance> balances = pageResult.getRecords();
        if (balances == null) {
            balances = Collections.emptyList();
        }

        long mismatchCount = 0;
        for (AccountBalance balance : balances) {
            if (!accountFlowService.verifyBalance(balance.getUserId(), balance.getTokenId())) {
                mismatchCount++;
                createAlert(balance);
            }
        }

        log.info("Sample reconciliation completed. Checked: {}, Mismatches: {}", balances.size(), mismatchCount);
    }

    private void createAlert(AccountBalance balance) {
        log.error("CRITICAL: Balance mismatch detected for userId={}, tokenId={}, available={}, frozen={}",
                balance.getUserId(), balance.getTokenId(),
                balance.getAvailableBalance(), balance.getFrozenBalance());

        alertService.alert("BALANCE_MISMATCH", AlertLevel.CRITICAL,
                String.format("userId=%s, tokenId=%d, available=%d, frozen=%d",
                        balance.getUserId(), balance.getTokenId(),
                        balance.getAvailableBalance(), balance.getFrozenBalance()));
    }
}
