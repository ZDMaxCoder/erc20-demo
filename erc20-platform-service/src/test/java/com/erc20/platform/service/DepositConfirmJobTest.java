package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import com.erc20.platform.domain.entity.DepositRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositConfirmJobTest {

    @Mock private DepositRecordMapper depositRecordMapper;
    @Mock private BlockSyncProgressMapper blockSyncProgressMapper;
    @Mock private DepositService depositService;

    private DepositConfirmJob depositConfirmJob;

    @BeforeEach
    void setUp() {
        depositConfirmJob = new DepositConfirmJob(depositRecordMapper, blockSyncProgressMapper, depositService);
    }

    // --- 5.1 CONFIRMING at block 100, current 112, required=12 → creditDeposit called ---

    @Test
    void confirmDeposits_meetsThreshold_creditsDeposit() {
        DepositRecord deposit = DepositRecord.builder()
                .id(1L)
                .blockNumber(100L)
                .requiredConfirmations(12)
                .confirmations(0)
                .status(DepositStatus.CONFIRMING.getCode())
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .lastSyncedBlock(112L)
                .build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).updateById(any(DepositRecord.class));

        depositConfirmJob.confirmDeposits();

        verify(depositService).creditDeposit(1L);
        verify(depositRecordMapper).updateById(any(DepositRecord.class));
    }

    // --- 5.2 current block 110, required 12 → NOT credited (only 10 confirmations) ---

    @Test
    void confirmDeposits_belowThreshold_doesNotCredit() {
        DepositRecord deposit = DepositRecord.builder()
                .id(2L)
                .blockNumber(100L)
                .requiredConfirmations(12)
                .confirmations(0)
                .status(DepositStatus.CONFIRMING.getCode())
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .lastSyncedBlock(110L)
                .build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).updateById(any(DepositRecord.class));

        depositConfirmJob.confirmDeposits();

        verify(depositService, never()).creditDeposit(anyLong());
        verify(depositRecordMapper).updateById(any(DepositRecord.class));
    }

    // --- 5.3 multiple CONFIRMING deposits, only those meeting threshold get credited ---

    @Test
    void confirmDeposits_mixedThresholds_onlyCreditsQualified() {
        DepositRecord qualified = DepositRecord.builder()
                .id(1L)
                .blockNumber(100L)
                .requiredConfirmations(12)
                .confirmations(0)
                .status(DepositStatus.CONFIRMING.getCode())
                .build();

        DepositRecord notQualified = DepositRecord.builder()
                .id(2L)
                .blockNumber(108L)
                .requiredConfirmations(12)
                .confirmations(0)
                .status(DepositStatus.CONFIRMING.getCode())
                .build();

        doReturn(Arrays.asList(qualified, notQualified))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));

        BlockSyncProgress progress = BlockSyncProgress.builder()
                .lastSyncedBlock(112L)
                .build();
        doReturn(progress).when(blockSyncProgressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).updateById(any(DepositRecord.class));

        depositConfirmJob.confirmDeposits();

        verify(depositService).creditDeposit(1L);
        verify(depositService, never()).creditDeposit(2L);
    }

    // --- 15.1 query limited to 500 records ---

    @SuppressWarnings("unchecked")
    @Test
    void confirmDeposits_queryLimitedTo500Records() throws Exception {
        doReturn(Collections.emptyList())
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));

        depositConfirmJob.confirmDeposits();

        ArgumentCaptor<LambdaQueryWrapper<DepositRecord>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(depositRecordMapper).selectList(captor.capture());

        java.lang.reflect.Field lastSqlField =
                com.baomidou.mybatisplus.core.conditions.AbstractWrapper.class
                        .getDeclaredField("lastSql");
        lastSqlField.setAccessible(true);
        Object lastSqlObj = lastSqlField.get(captor.getValue());
        java.lang.reflect.Method getStringValue = lastSqlObj.getClass().getMethod("getStringValue");
        String lastSql = (String) getStringValue.invoke(lastSqlObj);
        assertTrue(lastSql != null && lastSql.contains("LIMIT 500"),
                "Query should include LIMIT 500 but lastSql was: " + lastSql);
    }
}
