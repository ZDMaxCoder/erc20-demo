package com.erc20.platform.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.AlertService;
import com.erc20.platform.service.DepositService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
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
    @Mock
    private Web3j web3j;
    @Mock
    private TokenConfigMapper tokenConfigMapper;
    @Mock
    private AlertService alertService;
    @Mock
    private Request<?, EthBlockNumber> blockNumberRequest;
    @Mock
    private Request<?, EthGetTransactionReceipt> receiptRequest;

    private MqCompensationJob compensationJob;

    @BeforeEach
    void setUp() {
        compensationJob = new MqCompensationJob(
                depositRecordMapper, withdrawRecordMapper, depositService, mqProducer,
                web3j, tokenConfigMapper, alertService);
    }

    // --- 7.1: deposit CONFIRMING for >30min → compensation triggered ---

    @Test
    void compensate_depositConfirmingOver30min_creditDepositCalled() throws Exception {
        DepositRecord deposit = DepositRecord.builder()
                .id(1L)
                .txHash("0xabc")
                .tokenId(100L)
                .status(DepositStatus.CONFIRMING.getCode())
                .updatedAt(new Date(System.currentTimeMillis() - 31 * 60 * 1000L))
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.emptyList())
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(100L)
                .depositConfirmBlocks(12)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(100L);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setBlockNumber("0x64"); // block 100

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xabc");
        doReturn(receiptResponse).when(receiptRequest).send();

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x70"); // block 112 → 12 confirmations
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(blockNumberResponse).when(blockNumberRequest).send();

        compensationJob.compensate();

        verify(depositService).creditDeposit(1L);
    }

    // --- 9.1: stuck deposit with sufficient confirmations → credit ---

    @Test
    void compensateStuckDeposits_sufficientConfirmations_creditDeposit() throws Exception {
        DepositRecord deposit = DepositRecord.builder()
                .id(2L)
                .txHash("0xdef")
                .tokenId(200L)
                .status(DepositStatus.CONFIRMING.getCode())
                .updatedAt(new Date(System.currentTimeMillis() - 31 * 60 * 1000L))
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.emptyList())
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(200L)
                .depositConfirmBlocks(6)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(200L);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setBlockNumber("0xC8"); // block 200

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xdef");
        doReturn(receiptResponse).when(receiptRequest).send();

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0xCE"); // block 206 → 6 confirmations (sufficient)
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(blockNumberResponse).when(blockNumberRequest).send();

        compensationJob.compensate();

        verify(depositService).creditDeposit(2L);
        verify(alertService, never()).alert(anyString(), any(AlertLevel.class), anyString(), anyString());
    }

    // --- 9.2: stuck deposit with insufficient confirmations → not credited, alert ---

    @Test
    void compensateStuckDeposits_insufficientConfirmations_notCreditedAlertRaised() throws Exception {
        DepositRecord deposit = DepositRecord.builder()
                .id(3L)
                .txHash("0xghi")
                .tokenId(300L)
                .status(DepositStatus.CONFIRMING.getCode())
                .updatedAt(new Date(System.currentTimeMillis() - 31 * 60 * 1000L))
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.emptyList())
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        TokenConfig tokenConfig = TokenConfig.builder()
                .id(300L)
                .depositConfirmBlocks(12)
                .build();
        doReturn(tokenConfig).when(tokenConfigMapper).selectById(300L);

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setBlockNumber("0x64"); // block 100

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        receiptResponse.setResult(receipt);
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xghi");
        doReturn(receiptResponse).when(receiptRequest).send();

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x69"); // block 105 → 5 confirmations (insufficient)
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(blockNumberResponse).when(blockNumberRequest).send();

        compensationJob.compensate();

        verify(depositService, never()).creditDeposit(anyLong());
        verify(depositRecordMapper).updateById(argThat(record ->
                record.getId() == 3L && record.getUpdatedAt() != null));
        verify(alertService).alert(eq("DEPOSIT_STUCK"), eq(AlertLevel.WARN),
                contains("0xghi"), eq("3"));
    }

    // --- 9.3: stuck deposit with no receipt on chain → CRITICAL alert ---

    @Test
    void compensateStuckDeposits_noReceiptOnChain_criticalAlert() throws Exception {
        DepositRecord deposit = DepositRecord.builder()
                .id(4L)
                .txHash("0xjkl")
                .tokenId(400L)
                .status(DepositStatus.CONFIRMING.getCode())
                .updatedAt(new Date(System.currentTimeMillis() - 31 * 60 * 1000L))
                .build();

        doReturn(Collections.singletonList(deposit))
                .when(depositRecordMapper).selectList(any(LambdaQueryWrapper.class));
        doReturn(Collections.emptyList())
                .when(withdrawRecordMapper).selectList(any(LambdaQueryWrapper.class));

        EthGetTransactionReceipt receiptResponse = new EthGetTransactionReceipt();
        // No result set → getTransactionReceipt() returns Optional.empty()
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xjkl");
        doReturn(receiptResponse).when(receiptRequest).send();

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x70");
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        doReturn(blockNumberResponse).when(blockNumberRequest).send();

        compensationJob.compensate();

        verify(depositService, never()).creditDeposit(anyLong());
        verify(alertService).alert(eq("DEPOSIT_TX_MISSING"), eq(AlertLevel.CRITICAL),
                contains("0xjkl"), eq("4"));
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
