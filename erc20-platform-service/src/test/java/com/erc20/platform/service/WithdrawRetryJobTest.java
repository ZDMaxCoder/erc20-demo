package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.TxStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.gateway.WithdrawTransactionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawRetryJobTest {

    @Mock private WithdrawRecordMapper withdrawRecordMapper;
    @Mock private WithdrawService withdrawService;
    @Mock private WithdrawTransactionSender transactionSender;

    private WithdrawRetryJob retryJob;

    @BeforeEach
    void setUp() {
        retryJob = new WithdrawRetryJob(withdrawRecordMapper, withdrawService, transactionSender);
    }

    // --- 11.1 BROADCASTING older than 10min, chain confirmed → confirmWithdraw called ---

    @Test
    void scan_broadcastingConfirmedOnChain_callsConfirmWithdraw() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.BROADCASTING.getCode())
                .retryCount(0)
                .createdAt(minutesAgo(15))
                .updatedAt(minutesAgo(15))
                .build();

        doReturn(Collections.singletonList(record)).when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(TxStatus.CONFIRMED).when(transactionSender).queryTransactionStatus("0xtxhash001");

        retryJob.scanStuckWithdrawals();

        verify(withdrawService).confirmWithdraw(eq(1L), eq("0xtxhash001"), anyLong());
    }

    // --- 11.2 BROADCASTING older than 10min, chain dropped → reset to APPROVED ---

    @Test
    void scan_broadcastingDroppedOnChain_resetsToApproved() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.BROADCASTING.getCode())
                .retryCount(0)
                .createdAt(minutesAgo(15))
                .updatedAt(minutesAgo(15))
                .build();

        doReturn(Collections.singletonList(record)).when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(TxStatus.PENDING).when(transactionSender).queryTransactionStatus("0xtxhash001");

        retryJob.scanStuckWithdrawals();

        verify(withdrawRecordMapper).updateById(any(WithdrawRecord.class));
        verify(withdrawService, never()).confirmWithdraw(anyLong(), anyString(), anyLong());
    }

    // --- 11.3 retry_count >= 3 → FAILED + alert ---

    @Test
    void scan_retryCountExceeded_setsFailedAndAlerts() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.BROADCASTING.getCode())
                .retryCount(3)
                .createdAt(minutesAgo(15))
                .updatedAt(minutesAgo(15))
                .build();

        doReturn(Collections.singletonList(record)).when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        retryJob.scanStuckWithdrawals();

        verify(withdrawService).failWithdraw(eq(1L), anyString());
    }

    // --- Also test APPROVED stuck records ---

    @Test
    void scan_approvedOlderThan5min_callsExecuteWithdraw() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(2L)
                .status(WithdrawStatus.APPROVED.getCode())
                .retryCount(0)
                .createdAt(minutesAgo(10))
                .updatedAt(minutesAgo(10))
                .build();

        doReturn(Collections.singletonList(record)).when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        retryJob.scanStuckWithdrawals();

        verify(withdrawService).executeWithdraw(2L);
    }

    private Date minutesAgo(int minutes) {
        return new Date(System.currentTimeMillis() - minutes * 60 * 1000L);
    }
}
