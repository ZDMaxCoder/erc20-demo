package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.common.util.AddressUtil;
import com.erc20.platform.common.util.AmountUtil;
import com.erc20.platform.common.util.IdempotentKeyGenerator;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.dto.AccountOperateRequest;
import com.erc20.platform.service.dto.WithdrawRequest;
import com.erc20.platform.service.gateway.WithdrawMessagePublisher;
import com.erc20.platform.service.gateway.WithdrawTransactionSender;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WithdrawService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 30;

    private final WithdrawRecordMapper withdrawRecordMapper;
    private final TokenConfigMapper tokenConfigMapper;
    private final WalletConfigMapper walletConfigMapper;
    private final AccountService accountService;
    private final WithdrawTransactionSender transactionSender;
    private final WithdrawMessagePublisher messagePublisher;
    private final WithdrawStateMachine stateMachine;
    private final RedissonClient redissonClient;
    private final BusinessMetrics businessMetrics;

    public WithdrawService(WithdrawRecordMapper withdrawRecordMapper,
                           TokenConfigMapper tokenConfigMapper,
                           WalletConfigMapper walletConfigMapper,
                           AccountService accountService,
                           WithdrawTransactionSender transactionSender,
                           WithdrawMessagePublisher messagePublisher,
                           WithdrawStateMachine stateMachine,
                           RedissonClient redissonClient,
                           BusinessMetrics businessMetrics) {
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.tokenConfigMapper = tokenConfigMapper;
        this.walletConfigMapper = walletConfigMapper;
        this.accountService = accountService;
        this.transactionSender = transactionSender;
        this.messagePublisher = messagePublisher;
        this.stateMachine = stateMachine;
        this.redissonClient = redissonClient;
        this.businessMetrics = businessMetrics;
    }

    @Transactional
    public WithdrawRecord createWithdraw(WithdrawRequest request) {
        TokenConfig tokenConfig = getEnabledToken(request.getTokenId());

        if (!AddressUtil.isValid(request.getToAddress())) {
            throw new BizException(ErrorCode.ADDRESS_INVALID);
        }

        WithdrawRecord existing = withdrawRecordMapper.selectOne(
                new LambdaQueryWrapper<WithdrawRecord>()
                        .eq(WithdrawRecord::getRequestId, request.getRequestId()));
        if (existing != null) {
            return existing;
        }

        long feeAmount = tokenConfig.getWithdrawFeeAmount();
        long totalFreeze = request.getAmount() + feeAmount;

        String idempotentKey = IdempotentKeyGenerator.withdrawKey(request.getRequestId());

        accountService.freeze(AccountOperateRequest.builder()
                .userId(request.getUserId())
                .tokenId(request.getTokenId())
                .amount(totalFreeze)
                .amountExponent(request.getAmountExponent())
                .flowType(FlowType.FREEZE)
                .bizId(null)
                .idempotentKey(idempotentKey + "_FREEZE")
                .build());

        WithdrawRecord record = WithdrawRecord.builder()
                .requestId(request.getRequestId())
                .idempotentKey(idempotentKey)
                .userId(request.getUserId())
                .tokenId(request.getTokenId())
                .toAddress(request.getToAddress())
                .amount(request.getAmount())
                .amountExponent(request.getAmountExponent())
                .feeAmount(feeAmount)
                .status(WithdrawStatus.PENDING_REVIEW.getCode())
                .retryCount(0)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        withdrawRecordMapper.insert(record);
        return record;
    }

    public void approve(long withdrawId, String operator) {
        WithdrawRecord record = getWithdrawRecord(withdrawId);
        WithdrawStatus currentStatus = parseStatus(record.getStatus());
        stateMachine.assertTransition(currentStatus, WithdrawStatus.APPROVED);

        record.setStatus(WithdrawStatus.APPROVED.getCode());
        record.setUpdatedAt(new Date());
        withdrawRecordMapper.updateById(record);

        log.info("Withdrawal {} approved by {}", withdrawId, operator);
        messagePublisher.sendExecuteMessage(withdrawId);
    }

    public void reject(long withdrawId, String operator, String reason) {
        WithdrawRecord record = getWithdrawRecord(withdrawId);
        WithdrawStatus currentStatus = parseStatus(record.getStatus());
        stateMachine.assertTransition(currentStatus, WithdrawStatus.REJECTED);

        long totalUnfreeze = record.getAmount() + record.getFeeAmount();
        accountService.unfreeze(AccountOperateRequest.builder()
                .userId(record.getUserId())
                .tokenId(record.getTokenId())
                .amount(totalUnfreeze)
                .amountExponent(record.getAmountExponent())
                .flowType(FlowType.UNFREEZE)
                .bizId(record.getId())
                .idempotentKey(record.getIdempotentKey() + "_UNFREEZE_REJECT")
                .build());

        record.setStatus(WithdrawStatus.REJECTED.getCode());
        record.setErrorMessage(reason);
        record.setUpdatedAt(new Date());
        withdrawRecordMapper.updateById(record);

        log.info("Withdrawal {} rejected by {}: {}", withdrawId, operator, reason);
    }

    public void executeWithdraw(long withdrawId) {
        RLock lock = redissonClient.getLock("withdraw:" + withdrawId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring lock for withdrawal {}", withdrawId);
            return;
        }

        if (!acquired) {
            log.warn("Failed to acquire lock for withdrawal {}", withdrawId);
            return;
        }

        try {
            doExecuteWithdraw(withdrawId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doExecuteWithdraw(long withdrawId) {
        WithdrawRecord record = getWithdrawRecord(withdrawId);

        if (!WithdrawStatus.APPROVED.getCode().equals(record.getStatus())) {
            log.info("Withdrawal {} is not APPROVED (current={}), skipping", withdrawId, record.getStatus());
            return;
        }

        TokenConfig tokenConfig = getEnabledToken(record.getTokenId());
        WalletConfig hotWallet = getHotWallet();

        BigInteger chainAmount = AmountUtil.toChainAmount(
                record.getAmount(), record.getAmountExponent(), tokenConfig.getDecimals());

        record.setStatus(WithdrawStatus.SIGNING.getCode());
        record.setUpdatedAt(new Date());
        withdrawRecordMapper.updateById(record);

        try {
            TransactionRecord txRecord = transactionSender.sendERC20Transfer(
                    hotWallet.getAddress(), record.getToAddress(),
                    tokenConfig.getContractAddress(), chainAmount);

            record.setTxHash(txRecord.getTxHash());
            record.setStatus(WithdrawStatus.BROADCASTING.getCode());
            record.setUpdatedAt(new Date());
            withdrawRecordMapper.updateById(record);

            log.info("Withdrawal {} broadcast, txHash={}", withdrawId, txRecord.getTxHash());
        } catch (Exception e) {
            log.error("Failed to execute withdrawal {}", withdrawId, e);
            record.setStatus(WithdrawStatus.APPROVED.getCode());
            record.setRetryCount(record.getRetryCount() + 1);
            record.setErrorMessage(e.getMessage());
            record.setUpdatedAt(new Date());
            withdrawRecordMapper.updateById(record);
        }
    }

    @Transactional
    public void confirmWithdraw(long withdrawId, String txHash, long blockNumber) {
        RLock lock = redissonClient.getLock("withdraw:" + withdrawId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!acquired) {
            return;
        }

        try {
            doConfirmWithdraw(withdrawId, txHash, blockNumber);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doConfirmWithdraw(long withdrawId, String txHash, long blockNumber) {
        WithdrawRecord record = getWithdrawRecord(withdrawId);
        WithdrawStatus currentStatus = parseStatus(record.getStatus());

        if (currentStatus == WithdrawStatus.SUCCESS || currentStatus == WithdrawStatus.FAILED) {
            log.info("Withdrawal {} already in terminal state {}, skipping", withdrawId, currentStatus);
            return;
        }

        if (!stateMachine.canTransition(currentStatus, WithdrawStatus.PENDING_CONFIRM)
                && currentStatus != WithdrawStatus.PENDING_CONFIRM) {
            stateMachine.assertTransition(currentStatus, WithdrawStatus.SUCCESS);
        }

        record.setStatus(WithdrawStatus.SUCCESS.getCode());
        record.setTxHash(txHash);
        record.setUpdatedAt(new Date());
        withdrawRecordMapper.updateById(record);

        accountService.decreaseFrozen(AccountOperateRequest.builder()
                .userId(record.getUserId())
                .tokenId(record.getTokenId())
                .amount(record.getAmount())
                .amountExponent(record.getAmountExponent())
                .flowType(FlowType.WITHDRAW)
                .bizId(record.getId())
                .idempotentKey(record.getIdempotentKey() + "_WITHDRAW")
                .build());

        if (record.getFeeAmount() > 0) {
            accountService.decreaseFrozen(AccountOperateRequest.builder()
                    .userId(record.getUserId())
                    .tokenId(record.getTokenId())
                    .amount(record.getFeeAmount())
                    .amountExponent(record.getAmountExponent())
                    .flowType(FlowType.WITHDRAW_FEE)
                    .bizId(record.getId())
                    .idempotentKey(record.getIdempotentKey() + "_WITHDRAW_FEE")
                    .build());
        }

        businessMetrics.incrementWithdraw();
        log.info("Withdrawal {} confirmed, txHash={}, block={}", withdrawId, txHash, blockNumber);
    }

    @Transactional
    public void failWithdraw(long withdrawId, String reason) {
        RLock lock = redissonClient.getLock("withdraw:" + withdrawId);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!acquired) {
            return;
        }

        try {
            doFailWithdraw(withdrawId, reason);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doFailWithdraw(long withdrawId, String reason) {
        WithdrawRecord record = getWithdrawRecord(withdrawId);
        WithdrawStatus currentStatus = parseStatus(record.getStatus());

        if (currentStatus == WithdrawStatus.FAILED) {
            return;
        }

        stateMachine.assertTransition(currentStatus, WithdrawStatus.FAILED);

        long totalUnfreeze = record.getAmount() + record.getFeeAmount();
        accountService.unfreeze(AccountOperateRequest.builder()
                .userId(record.getUserId())
                .tokenId(record.getTokenId())
                .amount(totalUnfreeze)
                .amountExponent(record.getAmountExponent())
                .flowType(FlowType.UNFREEZE)
                .bizId(record.getId())
                .idempotentKey(record.getIdempotentKey() + "_UNFREEZE_FAIL")
                .build());

        record.setStatus(WithdrawStatus.FAILED.getCode());
        record.setErrorMessage(reason);
        record.setUpdatedAt(new Date());
        withdrawRecordMapper.updateById(record);

        log.info("Withdrawal {} failed: {}", withdrawId, reason);
    }

    private WithdrawRecord getWithdrawRecord(long withdrawId) {
        WithdrawRecord record = withdrawRecordMapper.selectById(withdrawId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Withdrawal not found: " + withdrawId);
        }
        return record;
    }

    private TokenConfig getEnabledToken(Long tokenId) {
        TokenConfig tokenConfig = tokenConfigMapper.selectOne(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getId, tokenId));
        if (tokenConfig == null || tokenConfig.getEnabled() != 1) {
            throw new BizException(ErrorCode.TOKEN_DISABLED);
        }
        return tokenConfig;
    }

    private WalletConfig getHotWallet() {
        WalletConfig wallet = walletConfigMapper.selectOne(
                new LambdaQueryWrapper<WalletConfig>()
                        .eq(WalletConfig::getWalletType, "HOT")
                        .eq(WalletConfig::getEnabled, 1));
        if (wallet == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "Hot wallet not configured");
        }
        return wallet;
    }

    private WithdrawStatus parseStatus(String code) {
        for (WithdrawStatus s : WithdrawStatus.values()) {
            if (s.getCode().equals(code)) {
                return s;
            }
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR, "Unknown withdraw status: " + code);
    }
}
