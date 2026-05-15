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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountFlowServiceTest {

    @Mock private AccountFlowMapper accountFlowMapper;
    @Mock private AccountBalanceMapper accountBalanceMapper;

    private AccountFlowService accountFlowService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        accountFlowService = new AccountFlowService(accountFlowMapper, accountBalanceMapper);
    }

    // --- 3.1 verifyBalance: flow replay matches current balance ---

    @Test
    void verifyBalance_consistentFlows_returnsTrue() {
        // increase(1000) IN, freeze(300) OUT-FREEZE, unfreeze(100) IN-UNFREEZE, decrease(200) OUT-WITHDRAW
        List<AccountFlow> flows = Arrays.asList(
                buildFlow(FlowType.DEPOSIT, FlowDirection.IN, 1000L),
                buildFlow(FlowType.FREEZE, FlowDirection.OUT, 300L),
                buildFlow(FlowType.UNFREEZE, FlowDirection.IN, 100L),
                buildFlow(FlowType.WITHDRAW, FlowDirection.OUT, 200L)
        );
        doReturn(flows).when(accountFlowMapper).selectList(any(LambdaQueryWrapper.class));

        // After: available = 1000 - 300 + 100 = 800, frozen = 0 + 300 - 100 - 200 = 0
        AccountBalance balance = AccountBalance.builder()
                .userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(800L).frozenBalance(0L)
                .build();
        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));

        boolean result = accountFlowService.verifyBalance(USER_ID, TOKEN_ID);

        assertTrue(result);
    }

    // --- 3.2 verifyBalance: tampered balance → returns false ---

    @Test
    void verifyBalance_tamperedBalance_returnsFalse() {
        List<AccountFlow> flows = Arrays.asList(
                buildFlow(FlowType.DEPOSIT, FlowDirection.IN, 1000L),
                buildFlow(FlowType.WITHDRAW, FlowDirection.OUT, 200L)
        );
        doReturn(flows).when(accountFlowMapper).selectList(any(LambdaQueryWrapper.class));

        // Tampered: should be 800 but set to 900
        AccountBalance balance = AccountBalance.builder()
                .userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(900L).frozenBalance(0L)
                .build();
        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));

        boolean result = accountFlowService.verifyBalance(USER_ID, TOKEN_ID);

        assertFalse(result);
    }

    // --- 3.3 queryFlows: paginated results ---

    @SuppressWarnings("unchecked")
    @Test
    void queryFlows_returnsPaginatedResults() {
        AccountFlow flow1 = buildFlow(FlowType.DEPOSIT, FlowDirection.IN, 1000L);
        AccountFlow flow2 = buildFlow(FlowType.WITHDRAW, FlowDirection.OUT, 500L);

        Page<AccountFlow> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(flow1, flow2));
        mockPage.setTotal(2);

        doReturn(mockPage).when(accountFlowMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));

        IPage<AccountFlow> result = accountFlowService.queryFlows(USER_ID, TOKEN_ID, 1, 10);

        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
        assertEquals(2, result.getTotal());
    }

    // --- 3.1 supplement: recordFlow idempotent ---

    @Test
    void recordFlow_duplicateKey_treatedAsSuccess() {
        doThrow(new DuplicateKeyException("duplicate")).when(accountFlowMapper).insert(any(AccountFlow.class));

        AccountFlow flow = buildFlow(FlowType.DEPOSIT, FlowDirection.IN, 1000L);
        accountFlowService.recordFlow(flow);

        verify(accountFlowMapper).insert(any(AccountFlow.class));
    }

    private AccountFlow buildFlow(FlowType type, FlowDirection direction, long amount) {
        return AccountFlow.builder()
                .userId(USER_ID).tokenId(TOKEN_ID)
                .flowType(type.getCode())
                .flowDirection(direction.getCode())
                .amount(amount).amountExponent(6)
                .balanceBefore(0L).balanceAfter(amount)
                .idempotentKey("key_" + System.nanoTime())
                .createdAt(new Date())
                .build();
    }
}
