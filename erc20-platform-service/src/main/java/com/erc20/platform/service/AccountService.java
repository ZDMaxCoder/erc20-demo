package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.FlowDirection;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.dal.mapper.AccountBalanceMapper;
import com.erc20.platform.dal.mapper.AccountFlowMapper;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.domain.entity.AccountFlow;
import com.erc20.platform.service.dto.AccountOperateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class AccountService {

    private static final int MAX_RETRY = 3;

    private final AccountBalanceMapper accountBalanceMapper;
    private final AccountFlowMapper accountFlowMapper;

    public AccountService(AccountBalanceMapper accountBalanceMapper,
                          AccountFlowMapper accountFlowMapper) {
        this.accountBalanceMapper = accountBalanceMapper;
        this.accountFlowMapper = accountFlowMapper;
    }

    @Transactional
    public void increaseAvailable(AccountOperateRequest request) {
        if (isIdempotentDuplicate(request)) {
            log.info("Duplicate idempotent key ignored: {}", request.getIdempotentKey());
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            AccountBalance balance = getOrCreateBalance(request.getUserId(), request.getTokenId());
            long before = balance.getAvailableBalance();
            balance.setAvailableBalance(before + request.getAmount());

            int rows = accountBalanceMapper.updateById(balance);
            if (rows > 0) {
                recordFlow(request, FlowDirection.IN, before, balance.getAvailableBalance());
                return;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Optimistic lock retry exhausted");
    }

    @Transactional
    public void freeze(AccountOperateRequest request) {
        if (isIdempotentDuplicate(request)) {
            log.info("Duplicate idempotent key ignored: {}", request.getIdempotentKey());
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            AccountBalance balance = getOrCreateBalance(request.getUserId(), request.getTokenId());

            if (balance.getAvailableBalance() < request.getAmount()) {
                throw new BizException(ErrorCode.INSUFFICIENT_BALANCE);
            }

            long beforeAvailable = balance.getAvailableBalance();
            balance.setAvailableBalance(beforeAvailable - request.getAmount());
            balance.setFrozenBalance(balance.getFrozenBalance() + request.getAmount());

            int rows = accountBalanceMapper.updateById(balance);
            if (rows > 0) {
                recordFlow(request, FlowDirection.OUT, beforeAvailable, balance.getAvailableBalance());
                return;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Optimistic lock retry exhausted");
    }

    @Transactional
    public void unfreeze(AccountOperateRequest request) {
        if (isIdempotentDuplicate(request)) {
            log.info("Duplicate idempotent key ignored: {}", request.getIdempotentKey());
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            AccountBalance balance = getOrCreateBalance(request.getUserId(), request.getTokenId());

            if (balance.getFrozenBalance() < request.getAmount()) {
                throw new BizException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient frozen balance");
            }

            long beforeAvailable = balance.getAvailableBalance();
            balance.setAvailableBalance(beforeAvailable + request.getAmount());
            balance.setFrozenBalance(balance.getFrozenBalance() - request.getAmount());

            int rows = accountBalanceMapper.updateById(balance);
            if (rows > 0) {
                recordFlow(request, FlowDirection.IN, beforeAvailable, balance.getAvailableBalance());
                return;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Optimistic lock retry exhausted");
    }

    @Transactional
    public void decreaseFrozen(AccountOperateRequest request) {
        if (isIdempotentDuplicate(request)) {
            log.info("Duplicate idempotent key ignored: {}", request.getIdempotentKey());
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            AccountBalance balance = getOrCreateBalance(request.getUserId(), request.getTokenId());

            if (balance.getFrozenBalance() < request.getAmount()) {
                throw new BizException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient frozen balance");
            }

            long beforeFrozen = balance.getFrozenBalance();
            balance.setFrozenBalance(beforeFrozen - request.getAmount());

            int rows = accountBalanceMapper.updateById(balance);
            if (rows > 0) {
                recordFlow(request, FlowDirection.OUT, beforeFrozen, balance.getFrozenBalance());
                return;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Optimistic lock retry exhausted");
    }

    @Transactional
    public void decreaseAvailable(AccountOperateRequest request) {
        if (isIdempotentDuplicate(request)) {
            log.info("Duplicate idempotent key ignored: {}", request.getIdempotentKey());
            return;
        }

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            AccountBalance balance = getOrCreateBalance(request.getUserId(), request.getTokenId());

            if (balance.getAvailableBalance() < request.getAmount()) {
                throw new BizException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient available balance");
            }

            long before = balance.getAvailableBalance();
            balance.setAvailableBalance(before - request.getAmount());

            int rows = accountBalanceMapper.updateById(balance);
            if (rows > 0) {
                recordFlow(request, FlowDirection.OUT, before, balance.getAvailableBalance());
                return;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Optimistic lock retry exhausted");
    }

    public AccountBalance getBalance(String userId, Long tokenId) {
        return getOrCreateBalance(userId, tokenId);
    }

    private AccountBalance getOrCreateBalance(String userId, Long tokenId) {
        AccountBalance balance = accountBalanceMapper.selectOne(
                new LambdaQueryWrapper<AccountBalance>()
                        .eq(AccountBalance::getUserId, userId)
                        .eq(AccountBalance::getTokenId, tokenId));

        if (balance == null) {
            balance = AccountBalance.builder()
                    .userId(userId)
                    .tokenId(tokenId)
                    .availableBalance(0L)
                    .frozenBalance(0L)
                    .amountExponent(0)
                    .version(0L)
                    .createdAt(new Date())
                    .updatedAt(new Date())
                    .build();
            accountBalanceMapper.insert(balance);
        }
        return balance;
    }

    private boolean isIdempotentDuplicate(AccountOperateRequest request) {
        AccountFlow existing = accountFlowMapper.selectOne(
                new LambdaQueryWrapper<AccountFlow>()
                        .eq(AccountFlow::getIdempotentKey, request.getIdempotentKey()));
        return existing != null;
    }

    private void recordFlow(AccountOperateRequest request, FlowDirection direction,
                            long balanceBefore, long balanceAfter) {
        AccountFlow flow = AccountFlow.builder()
                .userId(request.getUserId())
                .tokenId(request.getTokenId())
                .flowType(request.getFlowType().getCode())
                .flowDirection(direction.getCode())
                .amount(request.getAmount())
                .amountExponent(request.getAmountExponent())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .bizId(request.getBizId())
                .idempotentKey(request.getIdempotentKey())
                .createdAt(new Date())
                .build();

        try {
            accountFlowMapper.insert(flow);
        } catch (DuplicateKeyException e) {
            log.info("Duplicate flow record ignored: {}", request.getIdempotentKey());
        }
    }
}
