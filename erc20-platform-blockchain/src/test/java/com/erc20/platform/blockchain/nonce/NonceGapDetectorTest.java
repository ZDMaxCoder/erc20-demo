package com.erc20.platform.blockchain.nonce;

import com.erc20.platform.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NonceGapDetectorTest {

    @Mock
    private NonceRedisOperations redisOps;
    @Mock
    private AlertService alertService;

    private NonceGapDetector detector;

    private static final int CHAIN_ID = 1;
    private static final String WALLET = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @BeforeEach
    void setUp() {
        detector = new NonceGapDetector(redisOps, alertService);
    }

    // --- Task 1.6: Timed out allocations are moved to gaps ---

    @Test
    void detectAndReclaim_timedOutAllocations_movedToGaps() {
        Set<Long> timedOut = new HashSet<>(Arrays.asList(3L, 7L));
        doReturn(Collections.singletonList(CHAIN_ID + ":" + WALLET))
                .when(redisOps).getAllAllocatedKeys();
        doReturn(timedOut)
                .when(redisOps).getTimedOutAllocations(eq(CHAIN_ID), eq(WALLET), anyLong());

        detector.detectAndReclaimTimedOutNonces();

        verify(redisOps).addToGaps(CHAIN_ID, WALLET, 3L);
        verify(redisOps).addToGaps(CHAIN_ID, WALLET, 7L);
        verify(redisOps).removeFromAllocated(CHAIN_ID, WALLET, 3L);
        verify(redisOps).removeFromAllocated(CHAIN_ID, WALLET, 7L);
    }

    @Test
    void detectAndReclaim_noTimedOutAllocations_noGapsAdded() {
        doReturn(Collections.singletonList(CHAIN_ID + ":" + WALLET))
                .when(redisOps).getAllAllocatedKeys();
        doReturn(Collections.emptySet())
                .when(redisOps).getTimedOutAllocations(eq(CHAIN_ID), eq(WALLET), anyLong());

        detector.detectAndReclaimTimedOutNonces();

        verify(redisOps, never()).addToGaps(anyInt(), anyString(), anyLong());
        verify(redisOps, never()).removeFromAllocated(anyInt(), anyString(), anyLong());
    }
}
