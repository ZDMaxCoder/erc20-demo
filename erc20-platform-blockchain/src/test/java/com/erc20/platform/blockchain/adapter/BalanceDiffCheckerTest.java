package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceDiffCheckerTest {

    @Mock
    private SafeERC20Caller safeERC20Caller;

    @InjectMocks
    private BalanceDiffChecker balanceDiffChecker;

    @Test
    void queryBalance_delegatesToSafeERC20Caller() {
        String contract = "0xdac17f958d2ee523a2206206994597c13d831ec7";
        String address = "0xabc123";
        BigInteger expected = BigInteger.valueOf(1000);
        when(safeERC20Caller.safeBalanceOf(contract, address)).thenReturn(expected);

        BigInteger result = balanceDiffChecker.queryBalance(contract, address);

        assertEquals(expected, result);
        verify(safeERC20Caller).safeBalanceOf(contract, address);
    }

    @Test
    void computeDiff_positiveResult() {
        BigInteger before = BigInteger.valueOf(1000);
        BigInteger after = BigInteger.valueOf(1500);

        BigInteger diff = balanceDiffChecker.computeDiff(before, after);

        assertEquals(BigInteger.valueOf(500), diff);
    }

    @Test
    void computeDiff_zeroDiff() {
        BigInteger before = BigInteger.valueOf(1000);
        BigInteger after = BigInteger.valueOf(1000);

        BigInteger diff = balanceDiffChecker.computeDiff(before, after);

        assertEquals(BigInteger.ZERO, diff);
    }

    @Test
    void computeDiff_nullBeforeReturnsNull() {
        BigInteger diff = balanceDiffChecker.computeDiff(null, BigInteger.valueOf(1000));
        assertNull(diff);
    }

    @Test
    void computeDiff_nullAfterReturnsNull() {
        BigInteger diff = balanceDiffChecker.computeDiff(BigInteger.valueOf(1000), null);
        assertNull(diff);
    }
}
