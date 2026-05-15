package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.FlowDirection;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.dal.mapper.AccountBalanceMapper;
import com.erc20.platform.dal.mapper.AccountFlowMapper;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.domain.entity.AccountFlow;
import com.erc20.platform.service.dto.AccountOperateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountBalanceMapper accountBalanceMapper;
    @Mock private AccountFlowMapper accountFlowMapper;

    private AccountService accountService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;
    private static final Integer EXPONENT = 6;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountBalanceMapper, accountFlowMapper);
    }

    // --- 1.1 increaseAvailable: initial balance 0, increase by 1000 ---

    @Test
    void increaseAvailable_initialBalance_increasesAndRecordsFlow() {
        AccountBalance balance = AccountBalance.builder()
                .id(1L).userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(0L).frozenBalance(0L)
                .amountExponent(EXPONENT).version(0L)
                .build();

        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountBalanceMapper).updateById(any(AccountBalance.class));
        doReturn(1).when(accountFlowMapper).insert(any(AccountFlow.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(1000L)
                .amountExponent(EXPONENT).flowType(FlowType.DEPOSIT)
                .bizId(100L).idempotentKey("deposit_001")
                .build();

        accountService.increaseAvailable(request);

        ArgumentCaptor<AccountBalance> balanceCaptor = ArgumentCaptor.forClass(AccountBalance.class);
        verify(accountBalanceMapper).updateById(balanceCaptor.capture());
        assertEquals(1000L, balanceCaptor.getValue().getAvailableBalance().longValue());

        ArgumentCaptor<AccountFlow> flowCaptor = ArgumentCaptor.forClass(AccountFlow.class);
        verify(accountFlowMapper).insert(flowCaptor.capture());
        AccountFlow flow = flowCaptor.getValue();
        assertEquals(FlowDirection.IN.getCode(), flow.getFlowDirection());
        assertEquals(0L, flow.getBalanceBefore().longValue());
        assertEquals(1000L, flow.getBalanceAfter().longValue());
    }

    // --- 1.2 increaseAvailable: same idempotentKey twice → balance only increased once ---

    @Test
    void increaseAvailable_duplicateIdempotentKey_onlyIncreasesOnce() {
        AccountFlow existingFlow = AccountFlow.builder()
                .id(1L).idempotentKey("deposit_001").build();
        doReturn(existingFlow).when(accountFlowMapper).selectOne(any(LambdaQueryWrapper.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(1000L)
                .amountExponent(EXPONENT).flowType(FlowType.DEPOSIT)
                .bizId(100L).idempotentKey("deposit_001")
                .build();

        accountService.increaseAvailable(request);

        verify(accountBalanceMapper, never()).updateById(any(AccountBalance.class));
    }

    // --- 1.3 freeze: 500 from available 1000 → available=500, frozen=500 ---

    @Test
    void freeze_sufficientBalance_freezesAndRecordsFlow() {
        AccountBalance balance = AccountBalance.builder()
                .id(1L).userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(1000L).frozenBalance(0L)
                .amountExponent(EXPONENT).version(0L)
                .build();

        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountBalanceMapper).updateById(any(AccountBalance.class));
        doReturn(1).when(accountFlowMapper).insert(any(AccountFlow.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(500L)
                .amountExponent(EXPONENT).flowType(FlowType.FREEZE)
                .bizId(200L).idempotentKey("freeze_001")
                .build();

        accountService.freeze(request);

        ArgumentCaptor<AccountBalance> balanceCaptor = ArgumentCaptor.forClass(AccountBalance.class);
        verify(accountBalanceMapper).updateById(balanceCaptor.capture());
        AccountBalance updated = balanceCaptor.getValue();
        assertEquals(500L, updated.getAvailableBalance().longValue());
        assertEquals(500L, updated.getFrozenBalance().longValue());

        ArgumentCaptor<AccountFlow> flowCaptor = ArgumentCaptor.forClass(AccountFlow.class);
        verify(accountFlowMapper).insert(flowCaptor.capture());
        assertEquals(FlowType.FREEZE.getCode(), flowCaptor.getValue().getFlowType());
    }

    // --- 1.4 freeze: 1500 from available 1000 → BizException ---

    @Test
    void freeze_insufficientBalance_throwsBizException() {
        AccountBalance balance = AccountBalance.builder()
                .id(1L).userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(1000L).frozenBalance(0L)
                .amountExponent(EXPONENT).version(0L)
                .build();

        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(1500L)
                .amountExponent(EXPONENT).flowType(FlowType.FREEZE)
                .bizId(200L).idempotentKey("freeze_002")
                .build();

        BizException ex = assertThrows(BizException.class, () -> accountService.freeze(request));
        assertTrue(ex.getMessage().contains("Insufficient balance"));
        verify(accountBalanceMapper, never()).updateById(any());
    }

    // --- 1.5 unfreeze: 500 from frozen 500 → available=1000, frozen=0 ---

    @Test
    void unfreeze_sufficientFrozen_unfreezesAndRecordsFlow() {
        AccountBalance balance = AccountBalance.builder()
                .id(1L).userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(500L).frozenBalance(500L)
                .amountExponent(EXPONENT).version(0L)
                .build();

        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountBalanceMapper).updateById(any(AccountBalance.class));
        doReturn(1).when(accountFlowMapper).insert(any(AccountFlow.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(500L)
                .amountExponent(EXPONENT).flowType(FlowType.UNFREEZE)
                .bizId(200L).idempotentKey("unfreeze_001")
                .build();

        accountService.unfreeze(request);

        ArgumentCaptor<AccountBalance> balanceCaptor = ArgumentCaptor.forClass(AccountBalance.class);
        verify(accountBalanceMapper).updateById(balanceCaptor.capture());
        AccountBalance updated = balanceCaptor.getValue();
        assertEquals(1000L, updated.getAvailableBalance().longValue());
        assertEquals(0L, updated.getFrozenBalance().longValue());

        ArgumentCaptor<AccountFlow> flowCaptor = ArgumentCaptor.forClass(AccountFlow.class);
        verify(accountFlowMapper).insert(flowCaptor.capture());
        assertEquals(FlowType.UNFREEZE.getCode(), flowCaptor.getValue().getFlowType());
    }

    // --- 1.6 decreaseFrozen: 500 from frozen 500 → frozen=0, available unchanged ---

    @Test
    void decreaseFrozen_sufficientFrozen_decreasesAndRecordsFlow() {
        AccountBalance balance = AccountBalance.builder()
                .id(1L).userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(500L).frozenBalance(500L)
                .amountExponent(EXPONENT).version(0L)
                .build();

        doReturn(balance).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountBalanceMapper).updateById(any(AccountBalance.class));
        doReturn(1).when(accountFlowMapper).insert(any(AccountFlow.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(500L)
                .amountExponent(EXPONENT).flowType(FlowType.WITHDRAW)
                .bizId(300L).idempotentKey("withdraw_001")
                .build();

        accountService.decreaseFrozen(request);

        ArgumentCaptor<AccountBalance> balanceCaptor = ArgumentCaptor.forClass(AccountBalance.class);
        verify(accountBalanceMapper).updateById(balanceCaptor.capture());
        AccountBalance updated = balanceCaptor.getValue();
        assertEquals(500L, updated.getAvailableBalance().longValue());
        assertEquals(0L, updated.getFrozenBalance().longValue());

        ArgumentCaptor<AccountFlow> flowCaptor = ArgumentCaptor.forClass(AccountFlow.class);
        verify(accountFlowMapper).insert(flowCaptor.capture());
        assertEquals(FlowType.WITHDRAW.getCode(), flowCaptor.getValue().getFlowType());
        assertEquals(FlowDirection.OUT.getCode(), flowCaptor.getValue().getFlowDirection());
    }

    // --- 1.7 getBalance: non-existent account → creates with zeros ---

    @Test
    void getBalance_nonExistent_createsWithZeros() {
        doReturn(null).when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(accountBalanceMapper).insert(any(AccountBalance.class));

        AccountBalance result = accountService.getBalance(USER_ID, TOKEN_ID);

        assertNotNull(result);
        assertEquals(0L, result.getAvailableBalance().longValue());
        assertEquals(0L, result.getFrozenBalance().longValue());
        verify(accountBalanceMapper).insert(any(AccountBalance.class));
    }

    // --- 1.8 concurrent: two freeze 600 from available 1000 → exactly one succeeds ---

    @Test
    void freeze_optimisticLockConflict_retriesAndEventuallyFails() {
        doAnswer(invocation -> AccountBalance.builder()
                .id(1L).userId(USER_ID).tokenId(TOKEN_ID)
                .availableBalance(1000L).frozenBalance(0L)
                .amountExponent(EXPONENT).version(0L)
                .build())
                .when(accountBalanceMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(0).when(accountBalanceMapper).updateById(any(AccountBalance.class));

        AccountOperateRequest request = AccountOperateRequest.builder()
                .userId(USER_ID).tokenId(TOKEN_ID).amount(600L)
                .amountExponent(EXPONENT).flowType(FlowType.FREEZE)
                .bizId(200L).idempotentKey("freeze_concurrent")
                .build();

        BizException ex = assertThrows(BizException.class, () -> accountService.freeze(request));
        assertTrue(ex.getMessage().contains("retry"));
        verify(accountBalanceMapper, atLeast(3)).selectOne(any(LambdaQueryWrapper.class));
    }
}
