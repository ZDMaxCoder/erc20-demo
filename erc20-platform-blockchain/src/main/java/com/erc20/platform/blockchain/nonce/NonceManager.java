package com.erc20.platform.blockchain.nonce;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.dal.mapper.NonceRecordMapper;
import com.erc20.platform.domain.entity.NonceRecord;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NonceManager {

    private static final String LOCK_KEY_PREFIX = "nonce:%d:%s";
    private static final long LOCK_WAIT_SECONDS = 10;
    private static final long LOCK_LEASE_SECONDS = 5;

    private final NonceRedisOperations redisOps;
    private final NonceRecordMapper nonceRecordMapper;
    private final RedissonClient redissonClient;
    private final Web3j web3j;

    public NonceManager(NonceRedisOperations redisOps,
                        NonceRecordMapper nonceRecordMapper,
                        RedissonClient redissonClient,
                        Web3j web3j) {
        this.redisOps = redisOps;
        this.nonceRecordMapper = nonceRecordMapper;
        this.redissonClient = redissonClient;
        this.web3j = web3j;
    }

    public long allocateNonce(int chainId, String walletAddress) {
        long allocated;
        boolean newlyInitialized = false;

        RLock lock = acquireLock(chainId, walletAddress);
        try {
            Long gapNonce = redisOps.popSmallestGap(chainId, walletAddress);
            if (gapNonce != null) {
                redisOps.addToAllocated(chainId, walletAddress, gapNonce);
                return gapNonce;
            }

            Long pending = redisOps.getPendingNonce(chainId, walletAddress);
            if (pending == null) {
                pending = fetchChainNonce(walletAddress);
                newlyInitialized = true;
            }

            allocated = pending;
            redisOps.setPendingNonce(chainId, walletAddress, pending + 1);
            redisOps.addToAllocated(chainId, walletAddress, allocated);
        } finally {
            releaseLock(lock);
        }

        if (newlyInitialized) {
            initDbRecord(chainId, walletAddress, allocated);
        }
        updateDbPendingCount(chainId, walletAddress, 1);
        return allocated;
    }

    public void confirmNonce(int chainId, String walletAddress, long confirmedNonce) {
        redisOps.removeFromAllocated(chainId, walletAddress, confirmedNonce);
        NonceRecord record = findRecord(chainId, walletAddress);
        if (record != null) {
            if (confirmedNonce > record.getCurrentNonce()) {
                record.setCurrentNonce(confirmedNonce);
            }
            if (record.getPendingCount() != null && record.getPendingCount() > 0) {
                record.setPendingCount(record.getPendingCount() - 1);
            }
            nonceRecordMapper.updateById(record);
        }
    }

    public void releaseNonce(int chainId, String walletAddress, long nonce) {
        Long pending = redisOps.getPendingNonce(chainId, walletAddress);
        if (pending != null && nonce == pending - 1) {
            redisOps.setPendingNonce(chainId, walletAddress, nonce);
        } else {
            redisOps.addToGaps(chainId, walletAddress, nonce);
        }
        redisOps.removeFromAllocated(chainId, walletAddress, nonce);
        updateDbPendingCount(chainId, walletAddress, -1);
    }

    public void resetNonce(int chainId, String walletAddress) {
        long chainNonce = fetchChainNonce(walletAddress);
        redisOps.clearAll(chainId, walletAddress);
        redisOps.setPendingNonce(chainId, walletAddress, chainNonce);
        NonceRecord record = findRecord(chainId, walletAddress);
        if (record != null) {
            record.setCurrentNonce(chainNonce);
            record.setPendingCount(0);
            nonceRecordMapper.updateById(record);
        }
    }

    private RLock acquireLock(int chainId, String walletAddress) {
        String lockKey = String.format(LOCK_KEY_PREFIX, chainId, walletAddress);
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.LOCK_ACQUIRE_FAILED);
        }
        if (!acquired) {
            throw new BizException(ErrorCode.LOCK_ACQUIRE_FAILED);
        }
        return lock;
    }

    private void releaseLock(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private long fetchChainNonce(String walletAddress) {
        try {
            BigInteger nonce = web3j.ethGetTransactionCount(walletAddress, DefaultBlockParameterName.PENDING)
                    .send()
                    .getTransactionCount();
            return nonce.longValue();
        } catch (Exception e) {
            throw new BizException(ErrorCode.CHAIN_ERROR, "Failed to fetch nonce from chain");
        }
    }

    private NonceRecord findRecord(int chainId, String walletAddress) {
        LambdaQueryWrapper<NonceRecord> wrapper = new LambdaQueryWrapper<NonceRecord>()
                .eq(NonceRecord::getChainId, chainId)
                .eq(NonceRecord::getAddress, walletAddress);
        return nonceRecordMapper.selectOne(wrapper);
    }

    private void initDbRecord(int chainId, String walletAddress, long currentNonce) {
        NonceRecord existing = findRecord(chainId, walletAddress);
        if (existing == null) {
            NonceRecord record = NonceRecord.builder()
                    .chainId(chainId)
                    .address(walletAddress)
                    .currentNonce(currentNonce)
                    .pendingCount(0)
                    .build();
            nonceRecordMapper.insert(record);
        }
    }

    private void updateDbPendingCount(int chainId, String walletAddress, int delta) {
        NonceRecord record = findRecord(chainId, walletAddress);
        if (record != null) {
            int newCount = (record.getPendingCount() != null ? record.getPendingCount() : 0) + delta;
            record.setPendingCount(Math.max(0, newCount));
            nonceRecordMapper.updateById(record);
        }
    }
}
