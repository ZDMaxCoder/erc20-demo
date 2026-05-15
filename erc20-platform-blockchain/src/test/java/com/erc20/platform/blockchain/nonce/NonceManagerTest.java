package com.erc20.platform.blockchain.nonce;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.dal.mapper.NonceRecordMapper;
import com.erc20.platform.domain.entity.NonceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NonceManagerTest {

    @Mock
    private NonceRedisOperations redisOps;
    @Mock
    private NonceRecordMapper nonceRecordMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private Web3j web3j;
    @Mock
    private RLock rLock;
    @Mock
    private Request<?, EthGetTransactionCount> ethGetTxCountRequest;

    private NonceManager nonceManager;

    private static final int CHAIN_ID = 1;
    private static final String WALLET = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @BeforeEach
    void setUp() throws Exception {
        nonceManager = new NonceManager(redisOps, nonceRecordMapper, redissonClient, web3j);
        lenient().doReturn(rLock).when(redissonClient).getLock(anyString());
        lenient().doReturn(true).when(rLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        lenient().doReturn(true).when(rLock).isHeldByCurrentThread();
    }

    // --- Task 1.1: Sequential allocate returns 0, 1, 2 ---

    @Test
    void allocateNonce_firstCall_initializesFromChainAndReturnsZero() throws Exception {
        doReturn(null).when(redisOps).getPendingNonce(CHAIN_ID, WALLET);
        doReturn(null).when(redisOps).popSmallestGap(CHAIN_ID, WALLET);
        mockChainTransactionCount(BigInteger.ZERO);
        doReturn(null).when(nonceRecordMapper).selectOne(any());

        long nonce = nonceManager.allocateNonce(CHAIN_ID, WALLET);

        assertEquals(0L, nonce);
    }

    @Test
    void allocateNonce_secondCall_returnsOne() throws Exception {
        doReturn(null).when(redisOps).popSmallestGap(CHAIN_ID, WALLET);
        doReturn(1L).when(redisOps).getPendingNonce(CHAIN_ID, WALLET);

        long nonce = nonceManager.allocateNonce(CHAIN_ID, WALLET);

        assertEquals(1L, nonce);
    }

    @Test
    void allocateNonce_thirdCall_returnsTwo() throws Exception {
        doReturn(null).when(redisOps).popSmallestGap(CHAIN_ID, WALLET);
        doReturn(2L).when(redisOps).getPendingNonce(CHAIN_ID, WALLET);

        long nonce = nonceManager.allocateNonce(CHAIN_ID, WALLET);

        assertEquals(2L, nonce);
    }

    // --- Task 1.2: confirmNonce(5) updates current_nonce ---

    @Test
    void confirmNonce_updatesCurrentNonceToConfirmedValue() {
        NonceRecord existing = NonceRecord.builder()
                .id(1L)
                .address(WALLET)
                .chainId(CHAIN_ID)
                .currentNonce(3L)
                .pendingCount(5)
                .build();
        doReturn(existing).when(nonceRecordMapper).selectOne(any());

        nonceManager.confirmNonce(CHAIN_ID, WALLET, 5L);

        verify(redisOps).removeFromAllocated(CHAIN_ID, WALLET, 5L);
        ArgumentCaptor<NonceRecord> captor = ArgumentCaptor.forClass(NonceRecord.class);
        verify(nonceRecordMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getCurrentNonce().longValue());
    }

    // --- Task 1.3: Gap reuse after release ---

    @Test
    void allocateNonce_afterRelease_reusesGapNonce() throws Exception {
        // First three allocations: 0, 1, 2 (simulated by setup)
        // Release nonce 1 → goes to gaps
        // Next allocate should return 1 (from gaps), then 3

        // Simulate gap reuse: popSmallestGap returns 1 (the released nonce)
        doReturn(1L).when(redisOps).popSmallestGap(CHAIN_ID, WALLET);

        long reusedNonce = nonceManager.allocateNonce(CHAIN_ID, WALLET);
        assertEquals(1L, reusedNonce);

        // Next allocation: no more gaps, pending is 3
        doReturn(null).when(redisOps).popSmallestGap(CHAIN_ID, WALLET);
        doReturn(3L).when(redisOps).getPendingNonce(CHAIN_ID, WALLET);

        long nextNonce = nonceManager.allocateNonce(CHAIN_ID, WALLET);
        assertEquals(3L, nextNonce);
    }

    @Test
    void releaseNonce_middleNonce_addsToGaps() {
        doReturn(3L).when(redisOps).getPendingNonce(CHAIN_ID, WALLET);
        NonceRecord existing = NonceRecord.builder()
                .id(1L).address(WALLET).chainId(CHAIN_ID).currentNonce(0L).pendingCount(3).build();
        doReturn(existing).when(nonceRecordMapper).selectOne(any());

        nonceManager.releaseNonce(CHAIN_ID, WALLET, 1L);

        verify(redisOps).addToGaps(CHAIN_ID, WALLET, 1L);
        verify(redisOps).removeFromAllocated(CHAIN_ID, WALLET, 1L);
    }

    @Test
    void releaseNonce_lastNonce_decrementsPending() {
        doReturn(3L).when(redisOps).getPendingNonce(CHAIN_ID, WALLET);
        NonceRecord existing = NonceRecord.builder()
                .id(1L).address(WALLET).chainId(CHAIN_ID).currentNonce(0L).pendingCount(3).build();
        doReturn(existing).when(nonceRecordMapper).selectOne(any());

        nonceManager.releaseNonce(CHAIN_ID, WALLET, 2L);

        verify(redisOps).setPendingNonce(CHAIN_ID, WALLET, 2L);
        verify(redisOps, never()).addToGaps(anyInt(), anyString(), anyLong());
        verify(redisOps).removeFromAllocated(CHAIN_ID, WALLET, 2L);
    }

    // --- Task 1.4: resetNonce reinitializes from chain ---

    @Test
    void resetNonce_reinitializesFromChainAndClearsState() throws Exception {
        mockChainTransactionCount(BigInteger.valueOf(10));
        NonceRecord existing = NonceRecord.builder()
                .id(1L).address(WALLET).chainId(CHAIN_ID).currentNonce(5L).pendingCount(3).build();
        doReturn(existing).when(nonceRecordMapper).selectOne(any());

        nonceManager.resetNonce(CHAIN_ID, WALLET);

        verify(redisOps).setPendingNonce(CHAIN_ID, WALLET, 10L);
        verify(redisOps).clearAll(CHAIN_ID, WALLET);
        ArgumentCaptor<NonceRecord> captor = ArgumentCaptor.forClass(NonceRecord.class);
        verify(nonceRecordMapper).updateById(captor.capture());
        assertEquals(10L, captor.getValue().getCurrentNonce().longValue());
        assertEquals(0, captor.getValue().getPendingCount().intValue());
    }

    // --- Task 1.5: Concurrent test — 10 threads, unique nonces 0-9 ---

    @Test
    void allocateNonce_concurrent_allUnique() throws Exception {
        int threadCount = 10;
        ReentrantLock realLock = new ReentrantLock();
        AtomicLong pendingNonce = new AtomicLong(0);

        doAnswer(inv -> {
            long wait = inv.getArgument(0);
            return realLock.tryLock(wait, TimeUnit.SECONDS);
        }).when(rLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doAnswer(inv -> { realLock.unlock(); return null; }).when(rLock).unlock();

        doReturn(null).when(redisOps).popSmallestGap(anyInt(), anyString());
        doAnswer(inv -> {
            long val = pendingNonce.get();
            return val == 0 ? null : val;
        }).when(redisOps).getPendingNonce(anyInt(), anyString());

        doAnswer(inv -> {
            long nonce = inv.getArgument(2);
            pendingNonce.set(nonce);
            return null;
        }).when(redisOps).setPendingNonce(anyInt(), anyString(), anyLong());

        mockChainTransactionCount(BigInteger.ZERO);
        lenient().doReturn(null).when(nonceRecordMapper).selectOne(any());
        lenient().doReturn(1).when(nonceRecordMapper).insert(any());
        lenient().doReturn(1).when(nonceRecordMapper).updateById(any());

        Set<Long> allocatedNonces = ConcurrentHashMap.newKeySet();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long nonce = nonceManager.allocateNonce(CHAIN_ID, WALLET);
                    allocatedNonces.add(nonce);
                } catch (Exception e) {
                    fail("Thread failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount, allocatedNonces.size());
        for (long i = 0; i < threadCount; i++) {
            assertTrue(allocatedNonces.contains(i), "Missing nonce " + i);
        }
    }

    // --- Helper ---

    private void mockChainTransactionCount(BigInteger count) throws Exception {
        EthGetTransactionCount response = new EthGetTransactionCount();
        response.setResult("0x" + count.toString(16));
        doReturn(ethGetTxCountRequest).when(web3j).ethGetTransactionCount(anyString(), any());
        doReturn(response).when(ethGetTxCountRequest).send();
    }
}
