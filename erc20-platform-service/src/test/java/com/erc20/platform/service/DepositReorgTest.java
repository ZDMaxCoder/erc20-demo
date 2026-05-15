package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.UserAddressMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.service.dto.AccountOperateRequest;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositReorgTest {

    @Mock private DepositRecordMapper depositRecordMapper;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private UserAddressMapper userAddressMapper;
    @Mock private AccountService accountService;
    @Mock private BusinessMetrics businessMetrics;

    private DepositService depositService;

    @BeforeEach
    void setUp() {
        depositService = new DepositService(depositRecordMapper, tokenConfigMapper,
                userAddressMapper, accountService, businessMetrics);
    }

    // --- 7.1 deposit credited (SUCCESS, credited=1) then reorg → REORGED, balance deducted ---

    @Test
    void handleReorg_creditedDeposit_reversesAndSetsReorged() {
        DepositRecord deposit = DepositRecord.builder()
                .id(1L)
                .userId("user001")
                .tokenId(1L)
                .amount(10000L)
                .amountExponent(2)
                .blockNumber(100L)
                .status(DepositStatus.SUCCESS.getCode())
                .credited(1)
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).updateById(any(DepositRecord.class));

        depositService.handleReorg(Collections.singletonList(100L));

        verify(accountService).decreaseAvailable(any(AccountOperateRequest.class));

        ArgumentCaptor<DepositRecord> captor = ArgumentCaptor.forClass(DepositRecord.class);
        verify(depositRecordMapper).updateById(captor.capture());
        assertEquals(DepositStatus.REORGED.getCode(), captor.getValue().getStatus());
    }

    // --- 7.2 deposit not yet credited (CONFIRMING) then reorg → REORGED, no balance change ---

    @Test
    void handleReorg_unconfirmedDeposit_setsReorgedNoBalanceChange() {
        DepositRecord deposit = DepositRecord.builder()
                .id(2L)
                .userId("user001")
                .tokenId(1L)
                .amount(5000L)
                .amountExponent(2)
                .blockNumber(100L)
                .status(DepositStatus.CONFIRMING.getCode())
                .credited(0)
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).updateById(any(DepositRecord.class));

        depositService.handleReorg(Collections.singletonList(100L));

        verify(accountService, never()).decreaseAvailable(any());
        verify(accountService, never()).increaseAvailable(any());

        ArgumentCaptor<DepositRecord> captor = ArgumentCaptor.forClass(DepositRecord.class);
        verify(depositRecordMapper).updateById(captor.capture());
        assertEquals(DepositStatus.REORGED.getCode(), captor.getValue().getStatus());
    }
}
