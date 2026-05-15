package com.erc20.platform.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.DepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqCompensationJobTest {

    @Mock
    private DepositRecordMapper depositRecordMapper;
    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;
    @Mock
    private DepositService depositService;
    @Mock
    private MqProducer mqProducer;

    private MqCompensationJob compensationJob;

    @BeforeEach
    void setUp() {
        compensationJob = new MqCompensationJob(
                depositRecordMapper, withdrawRecordMapper, depositService, mqProducer);
    }

    // --- 7.1: deposit CONFIRMING for >30min → compensation triggered ---

    @Test
    void compensate_depositConfirmingOver30min_creditDepositCalled() {
        DepositRecord deposit = DepositRecord.builder()
                .id(1L)
                .status(DepositStatus.CONFIRMING.getCode())
                .updatedAt(new Date(System.currentTimeMillis() - 31 * 60 * 1000L))
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.emptyList())
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        compensationJob.compensate();

        verify(depositService).creditDeposit(1L);
    }

    // --- 7.2: withdraw APPROVED for >5min → re-publish execute message ---

    @Test
    void compensate_withdrawApprovedOver5min_executeMessageRePublished() {
        WithdrawRecord withdraw = WithdrawRecord.builder()
                .id(10L)
                .status(WithdrawStatus.APPROVED.getCode())
                .retryCount(0)
                .updatedAt(new Date(System.currentTimeMillis() - 6 * 60 * 1000L))
                .build();

        doReturn(Collections.emptyList())
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.singletonList(withdraw))
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        compensationJob.compensate();

        verify(mqProducer).send(
                eq(MqConstants.TOPIC_WITHDRAW_EXECUTE),
                eq(MqConstants.TAG_APPROVED),
                eq("10"),
                any());
        verify(withdrawRecordMapper).updateById(argThat(record ->
                record.getRetryCount() == 1));
    }

    // --- 7.3: already compensated 5 times → not compensated again ---

    @Test
    void compensate_withdrawRetryCountReachedMax_notCompensated() {
        WithdrawRecord withdraw = WithdrawRecord.builder()
                .id(20L)
                .status(WithdrawStatus.APPROVED.getCode())
                .retryCount(5)
                .updatedAt(new Date(System.currentTimeMillis() - 6 * 60 * 1000L))
                .build();

        doReturn(Collections.emptyList())
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.singletonList(withdraw))
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        compensationJob.compensate();

        verify(mqProducer, never()).send(anyString(), anyString(), anyString(), any());
        verify(withdrawRecordMapper, never()).updateById(any());
    }
}
