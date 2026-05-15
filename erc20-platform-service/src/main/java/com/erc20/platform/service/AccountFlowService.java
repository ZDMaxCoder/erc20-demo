package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erc20.platform.common.enums.FlowDirection;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.dal.mapper.AccountBalanceMapper;
import com.erc20.platform.dal.mapper.AccountFlowMapper;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.domain.entity.AccountFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AccountFlowService {

    private static final Set<String> INTERNAL_FLOW_TYPES = new HashSet<>(Arrays.asList(
            FlowType.FREEZE.getCode(),
            FlowType.UNFREEZE.getCode()
    ));

    private final AccountFlowMapper accountFlowMapper;
    private final AccountBalanceMapper accountBalanceMapper;

    public AccountFlowService(AccountFlowMapper accountFlowMapper,
                              AccountBalanceMapper accountBalanceMapper) {
        this.accountFlowMapper = accountFlowMapper;
        this.accountBalanceMapper = accountBalanceMapper;
    }

    public void recordFlow(AccountFlow flow) {
        try {
            accountFlowMapper.insert(flow);
        } catch (DuplicateKeyException e) {
            log.info("Duplicate flow record ignored: {}", flow.getIdempotentKey());
        }
    }

    public IPage<AccountFlow> queryFlows(String userId, Long tokenId, int page, int size) {
        Page<AccountFlow> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AccountFlow> wrapper = new LambdaQueryWrapper<AccountFlow>()
                .eq(AccountFlow::getUserId, userId)
                .eq(AccountFlow::getTokenId, tokenId)
                .orderByDesc(AccountFlow::getCreatedAt);
        return accountFlowMapper.selectPage(pageParam, wrapper);
    }

    public boolean verifyBalance(String userId, Long tokenId) {
        List<AccountFlow> flows = accountFlowMapper.selectList(
                new LambdaQueryWrapper<AccountFlow>()
                        .eq(AccountFlow::getUserId, userId)
                        .eq(AccountFlow::getTokenId, tokenId));

        long replayTotal = 0;
        for (AccountFlow flow : flows) {
            if (INTERNAL_FLOW_TYPES.contains(flow.getFlowType())) {
                continue;
            }
            if (FlowDirection.IN.getCode().equals(flow.getFlowDirection())) {
                replayTotal += flow.getAmount();
            } else {
                replayTotal -= flow.getAmount();
            }
        }

        AccountBalance balance = accountBalanceMapper.selectOne(
                new LambdaQueryWrapper<AccountBalance>()
                        .eq(AccountBalance::getUserId, userId)
                        .eq(AccountBalance::getTokenId, tokenId));

        if (balance == null) {
            return replayTotal == 0;
        }

        long currentTotal = balance.getAvailableBalance() + balance.getFrozenBalance();
        boolean match = replayTotal == currentTotal;
        if (!match) {
            log.error("Balance verification FAILED for userId={}, tokenId={}: replay={}, actual={}",
                    userId, tokenId, replayTotal, currentTotal);
        }
        return match;
    }
}
