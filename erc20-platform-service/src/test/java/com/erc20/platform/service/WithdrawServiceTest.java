package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.TransactionRecord;
import com.erc20.platform.domain.entity.WalletConfig;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.dto.AccountOperateRequest;
import com.erc20.platform.service.dto.WithdrawRequest;
import com.erc20.platform.service.gateway.WithdrawMessagePublisher;
import com.erc20.platform.service.gateway.WithdrawTransactionSender;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import com.erc20.platform.service.risk.RiskControlService;
import com.erc20.platform.service.risk.RiskResult;
import com.erc20.platform.common.enums.AlertLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    @Mock private WithdrawRecordMapper withdrawRecordMapper;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private WalletConfigMapper walletConfigMapper;
    @Mock private AccountService accountService;
    @Mock private WithdrawTransactionSender transactionSender;
    @Mock private WithdrawMessagePublisher messagePublisher;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;
    @Mock private BusinessMetrics businessMetrics;
    @Mock private RiskControlService riskControlService;
    @Mock private AlertService alertService;

    private WithdrawStateMachine stateMachine;
    private WithdrawService withdrawService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;
    private static final String TO_ADDRESS = "0x9999999999999999999999999999999999999999";
    private static final String HOT_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";

    @BeforeEach
    void setUp() {
        stateMachine = new WithdrawStateMachine();
        withdrawService = new WithdrawService(
                withdrawRecordMapper, tokenConfigMapper, walletConfigMapper,
                accountService, transactionSender, messagePublisher,
                stateMachine, redissonClient, businessMetrics, riskControlService,
                alertService);
    }

    private TokenConfig buildTokenConfig() {
        return TokenConfig.builder()
                .id(TOKEN_ID)
                .contractAddress(CONTRACT_ADDRESS)
                .decimals(6)
                .amountExponent(2)
                .minWithdrawAmount(100L)
                .withdrawFeeAmount(10L)
                .enabled(1)
                .build();
    }

    private WithdrawRequest buildWithdrawRequest() {
        return WithdrawRequest.builder()
                .requestId("req001")
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .toAddress(TO_ADDRESS)
                .amount(1000L)
                .amountExponent(2)
                .build();
    }

    private WalletConfig buildHotWallet() {
        return WalletConfig.builder()
                .address(HOT_WALLET)
                .walletType("HOT")
                .enabled(1)
                .build();
    }

    private void stubLock() throws InterruptedException {
        doReturn(rLock).when(redissonClient).getLock(anyString());
        doReturn(true).when(rLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        doReturn(true).when(rLock).isHeldByCurrentThread();
    }

    // ============================
    // 3.1 createWithdraw — happy path
    // ============================

    @Test
    void createWithdraw_validRequest_createsRecordWithPendingReview() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(withdrawRecordMapper).insert(any(WithdrawRecord.class));
        doReturn(RiskResult.manualReview("large amount")).when(riskControlService).checkWithdraw(any(WithdrawRecord.class));

        WithdrawRecord result = withdrawService.createWithdraw(request);

        assertNotNull(result);
        assertEquals(WithdrawStatus.PENDING_REVIEW.getCode(), result.getStatus());
        assertEquals(USER_ID, result.getUserId());
        assertEquals(TOKEN_ID, result.getTokenId());
        assertEquals(TO_ADDRESS, result.getToAddress());
        assertEquals(1000L, result.getAmount().longValue());
        assertEquals(10L, result.getFeeAmount().longValue());

        ArgumentCaptor<AccountOperateRequest> freezeCaptor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService).freeze(freezeCaptor.capture());
        AccountOperateRequest freezeReq = freezeCaptor.getValue();
        assertEquals(1010L, freezeReq.getAmount().longValue());
        assertEquals(FlowType.FREEZE, freezeReq.getFlowType());
    }

    // ============================
    // 3.2 insufficient balance
    // ============================

    @Test
    void createWithdraw_insufficientBalance_throwsBizException() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doThrow(new BizException(ErrorCode.INSUFFICIENT_BALANCE)).when(accountService).freeze(any());

        BizException ex = assertThrows(BizException.class, () -> withdrawService.createWithdraw(request));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE.getCode(), ex.getCode());
    }

    // ============================
    // 3.3 idempotent — same request_id returns existing
    // ============================

    @Test
    void createWithdraw_duplicateRequestId_returnsExistingRecord() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        WithdrawRecord existing = WithdrawRecord.builder()
                .id(1L)
                .requestId("req001")
                .status(WithdrawStatus.PENDING_REVIEW.getCode())
                .build();
        doReturn(existing).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        WithdrawRecord result = withdrawService.createWithdraw(request);

        assertSame(existing, result);
        verify(accountService, never()).freeze(any());
        verify(withdrawRecordMapper, never()).insert(any());
    }

    // ============================
    // 3.4 invalid address
    // ============================

    @Test
    void createWithdraw_invalidAddress_throwsBizException() {
        WithdrawRequest request = buildWithdrawRequest();
        request.setToAddress("not_a_valid_address");

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        BizException ex = assertThrows(BizException.class, () -> withdrawService.createWithdraw(request));
        assertEquals(ErrorCode.ADDRESS_INVALID.getCode(), ex.getCode());
    }

    // ============================
    // 3.5 token not enabled
    // ============================

    @Test
    void createWithdraw_tokenDisabled_throwsBizException() {
        WithdrawRequest request = buildWithdrawRequest();

        TokenConfig disabledToken = buildTokenConfig();
        disabledToken.setEnabled(0);
        doReturn(disabledToken).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        BizException ex = assertThrows(BizException.class, () -> withdrawService.createWithdraw(request));
        assertEquals(ErrorCode.TOKEN_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void createWithdraw_tokenNotFound_throwsBizException() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(null).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        BizException ex = assertThrows(BizException.class, () -> withdrawService.createWithdraw(request));
        assertEquals(ErrorCode.TOKEN_DISABLED.getCode(), ex.getCode());
    }

    // ============================
    // 4.1 risk check — AUTO_PASS
    // ============================

    @Test
    void createWithdraw_riskAutoPass_statusApprovedAndExecuteMessagePublished() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doAnswer(invocation -> {
            WithdrawRecord r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        }).when(withdrawRecordMapper).insert(any(WithdrawRecord.class));
        doReturn(RiskResult.pass()).when(riskControlService).checkWithdraw(any(WithdrawRecord.class));

        WithdrawRecord result = withdrawService.createWithdraw(request);

        assertEquals(WithdrawStatus.APPROVED.getCode(), result.getStatus());
        verify(riskControlService).checkWithdraw(any(WithdrawRecord.class));
        verify(messagePublisher).sendExecuteMessage(1L);
    }

    // ============================
    // 4.2 risk check — REJECT
    // ============================

    @Test
    void createWithdraw_riskReject_balanceUnfrozenAndStatusRejected() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(withdrawRecordMapper).insert(any(WithdrawRecord.class));
        doReturn(RiskResult.reject("blacklisted address")).when(riskControlService).checkWithdraw(any(WithdrawRecord.class));

        WithdrawRecord result = withdrawService.createWithdraw(request);

        assertEquals(WithdrawStatus.REJECTED.getCode(), result.getStatus());
        assertEquals("blacklisted address", result.getErrorMessage());

        ArgumentCaptor<AccountOperateRequest> captor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService).unfreeze(captor.capture());
        AccountOperateRequest unfreezeReq = captor.getValue();
        assertEquals(1010L, unfreezeReq.getAmount().longValue());
        assertEquals(FlowType.UNFREEZE, unfreezeReq.getFlowType());
    }

    // ============================
    // 4.3 risk check — NEED_MANUAL_REVIEW
    // ============================

    @Test
    void createWithdraw_riskNeedManualReview_statusPendingReview() {
        WithdrawRequest request = buildWithdrawRequest();

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(withdrawRecordMapper).insert(any(WithdrawRecord.class));
        doReturn(RiskResult.manualReview("large amount")).when(riskControlService).checkWithdraw(any(WithdrawRecord.class));

        WithdrawRecord result = withdrawService.createWithdraw(request);

        assertEquals(WithdrawStatus.PENDING_REVIEW.getCode(), result.getStatus());
        verify(riskControlService).checkWithdraw(any(WithdrawRecord.class));
        verify(messagePublisher, never()).sendExecuteMessage(anyLong());
    }

    // ============================
    // 5.1 approve — happy path
    // ============================

    @Test
    void approve_pendingReview_statusBecomesApprovedAndMqSent() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .status(WithdrawStatus.PENDING_REVIEW.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        withdrawService.approve(1L, "admin");

        assertEquals(WithdrawStatus.APPROVED.getCode(), record.getStatus());
        verify(messagePublisher).sendExecuteMessage(1L);
    }

    // ============================
    // 5.2 approve — illegal state
    // ============================

    @Test
    void approve_notPendingReview_throwsBizException() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .status(WithdrawStatus.BROADCASTING.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);

        assertThrows(BizException.class, () -> withdrawService.approve(1L, "admin"));
        verify(withdrawRecordMapper, never()).updateById(any());
    }

    // ============================
    // 5.3 reject — happy path
    // ============================

    @Test
    void reject_pendingReview_statusRejectedAndBalanceUnfrozen() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .status(WithdrawStatus.PENDING_REVIEW.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        withdrawService.reject(1L, "admin", "suspicious activity");

        assertEquals(WithdrawStatus.REJECTED.getCode(), record.getStatus());

        ArgumentCaptor<AccountOperateRequest> captor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService).unfreeze(captor.capture());
        AccountOperateRequest unfreezeReq = captor.getValue();
        assertEquals(1010L, unfreezeReq.getAmount().longValue());
        assertEquals(FlowType.UNFREEZE, unfreezeReq.getFlowType());
    }

    // ============================
    // 7.1 executeWithdraw — happy path
    // ============================

    @Test
    void executeWithdraw_approved_callsTransactionSenderAndBroadcasts() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .toAddress(TO_ADDRESS)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .status(WithdrawStatus.APPROVED.getCode())
                .retryCount(0)
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        TokenConfig tokenConfig = buildTokenConfig();
        doReturn(tokenConfig).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        WalletConfig hotWallet = buildHotWallet();
        doReturn(hotWallet).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        TransactionRecord txRecord = TransactionRecord.builder()
                .txHash("0xtxhash001")
                .nonce(42L)
                .build();
        doReturn(txRecord).when(transactionSender).sendERC20Transfer(
                eq(HOT_WALLET), eq(TO_ADDRESS), eq(CONTRACT_ADDRESS),
                any(BigInteger.class));

        withdrawService.executeWithdraw(1L);

        assertEquals(WithdrawStatus.BROADCASTING.getCode(), record.getStatus());
        assertEquals("0xtxhash001", record.getTxHash());
        verify(transactionSender).sendERC20Transfer(eq(HOT_WALLET), eq(TO_ADDRESS), eq(CONTRACT_ADDRESS),
                any(BigInteger.class));
    }

    // ============================
    // 7.2 transactionSender throws — stays APPROVED, retry incremented
    // ============================

    @Test
    void executeWithdraw_transactionSenderThrows_staysApprovedRetryIncremented() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .toAddress(TO_ADDRESS)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .status(WithdrawStatus.APPROVED.getCode())
                .retryCount(0)
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        TokenConfig tokenConfig = buildTokenConfig();
        doReturn(tokenConfig).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        WalletConfig hotWallet = buildHotWallet();
        doReturn(hotWallet).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        doThrow(new BizException(ErrorCode.BROADCAST_FAILED, "timeout"))
                .when(transactionSender).sendERC20Transfer(anyString(), anyString(), anyString(),
                        any(BigInteger.class));

        withdrawService.executeWithdraw(1L);

        assertEquals(WithdrawStatus.APPROVED.getCode(), record.getStatus());
        assertEquals(1, record.getRetryCount().intValue());
    }

    // ============================
    // 7.3 distributed lock acquired
    // ============================

    @Test
    void executeWithdraw_acquiresDistributedLock() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .toAddress(TO_ADDRESS)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .status(WithdrawStatus.APPROVED.getCode())
                .retryCount(0)
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        TokenConfig tokenConfig = buildTokenConfig();
        doReturn(tokenConfig).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        WalletConfig hotWallet = buildHotWallet();
        doReturn(hotWallet).when(walletConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        TransactionRecord txRecord = TransactionRecord.builder()
                .txHash("0xtxhash001")
                .nonce(42L)
                .build();
        doReturn(txRecord).when(transactionSender).sendERC20Transfer(
                anyString(), anyString(), anyString(), any(BigInteger.class));

        withdrawService.executeWithdraw(1L);

        verify(redissonClient).getLock("withdraw:1");
        verify(rLock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(rLock).unlock();
    }

    // ============================
    // 7.4 executeWithdraw when not APPROVED — no-op
    // ============================

    @Test
    void executeWithdraw_notApproved_noOp() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .status(WithdrawStatus.BROADCASTING.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);

        withdrawService.executeWithdraw(1L);

        verify(transactionSender, never()).sendERC20Transfer(anyString(), anyString(), anyString(),
                any(BigInteger.class));
    }

    // ============================
    // 9.1 confirmWithdraw — happy path
    // ============================

    @Test
    void confirmWithdraw_pendingConfirmRecord_statusSuccessAndFrozenDecreased() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.PENDING_CONFIRM.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        withdrawService.confirmWithdraw(1L, "0xtxhash001", 12345L);

        assertEquals(WithdrawStatus.SUCCESS.getCode(), record.getStatus());

        ArgumentCaptor<AccountOperateRequest> captor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService, times(2)).decreaseFrozen(captor.capture());
        long totalDecreased = 0;
        for (AccountOperateRequest req : captor.getAllValues()) {
            totalDecreased += req.getAmount();
        }
        assertEquals(1010L, totalDecreased);
    }

    // ============================
    // 9.2 failWithdraw — balance unfrozen
    // ============================

    @Test
    void failWithdraw_broadcastingRecord_statusFailedAndBalanceUnfrozen() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .status(WithdrawStatus.BROADCASTING.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        withdrawService.failWithdraw(1L, "tx reverted");

        assertEquals(WithdrawStatus.FAILED.getCode(), record.getStatus());

        ArgumentCaptor<AccountOperateRequest> captor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService).unfreeze(captor.capture());
        assertEquals(1010L, captor.getValue().getAmount().longValue());
        assertEquals(FlowType.UNFREEZE, captor.getValue().getFlowType());
    }

    // ============================
    // 9.3 concurrent confirmWithdraw — only first succeeds
    // ============================

    @Test
    void confirmWithdraw_alreadySuccess_noOp() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .status(WithdrawStatus.SUCCESS.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);

        withdrawService.confirmWithdraw(1L, "0xtxhash001", 12345L);

        verify(accountService, never()).decreaseFrozen(any());
        verify(withdrawRecordMapper, never()).updateById(any());
    }

    // ============================
    // revertConfirmedWithdraw — reorg scenario
    // ============================

    @Test
    void revertConfirmedWithdraw_successRecord_statusBroadcastingAndFrozenRestored() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .idempotentKey("WD_req001")
                .txHash("0xtxhash001")
                .status(WithdrawStatus.SUCCESS.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        withdrawService.revertConfirmedWithdraw(1L);

        assertEquals(WithdrawStatus.BROADCASTING.getCode(), record.getStatus());

        ArgumentCaptor<AccountOperateRequest> increaseCaptor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService, atLeastOnce()).increaseAvailable(increaseCaptor.capture());
        long totalIncreased = 0;
        for (AccountOperateRequest req : increaseCaptor.getAllValues()) {
            totalIncreased += req.getAmount();
            assertEquals(FlowType.ADJUSTMENT, req.getFlowType());
        }
        assertEquals(1010L, totalIncreased);

        ArgumentCaptor<AccountOperateRequest> freezeCaptor = ArgumentCaptor.forClass(AccountOperateRequest.class);
        verify(accountService, atLeastOnce()).freeze(freezeCaptor.capture());
        long totalFrozen = 0;
        for (AccountOperateRequest req : freezeCaptor.getAllValues()) {
            totalFrozen += req.getAmount();
        }
        assertEquals(1010L, totalFrozen);
    }

    @Test
    void revertConfirmedWithdraw_notInSuccessStatus_noOp() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .status(WithdrawStatus.BROADCASTING.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);

        withdrawService.revertConfirmedWithdraw(1L);

        verify(accountService, never()).increaseAvailable(any());
        verify(withdrawRecordMapper, never()).updateById(any());
    }

    @Test
    void revertConfirmedWithdraw_alreadyReverted_noOp() {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .status(WithdrawStatus.BROADCASTING.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);

        withdrawService.revertConfirmedWithdraw(1L);

        verify(accountService, never()).increaseAvailable(any());
        verify(withdrawRecordMapper, never()).updateById(any());
    }

    // ============================
    // 5.1 confirmWithdraw — matching actualAmount → no alert
    // ============================

    @Test
    void confirmWithdraw_matchingActualAmount_noAlert() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.PENDING_CONFIRM.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());
        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        // token decimals=6, amountExponent=2, amount=1000 → chainAmount = 1000 * 10^(6-2) = 10000000
        BigInteger matchingAmount = BigInteger.valueOf(10000000L);

        withdrawService.confirmWithdraw(1L, "0xtxhash001", 12345L, matchingAmount);

        assertEquals(WithdrawStatus.SUCCESS.getCode(), record.getStatus());
        verify(alertService, never()).alert(anyString(), any(AlertLevel.class), anyString());
    }

    // ============================
    // 5.2 confirmWithdraw — mismatched actualAmount → alert raised, still confirmed
    // ============================

    @Test
    void confirmWithdraw_mismatchedActualAmount_alertRaisedButStillConfirmed() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.PENDING_CONFIRM.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());
        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        BigInteger mismatchedAmount = BigInteger.valueOf(5000000L);

        withdrawService.confirmWithdraw(1L, "0xtxhash001", 12345L, mismatchedAmount);

        assertEquals(WithdrawStatus.SUCCESS.getCode(), record.getStatus());
        verify(alertService).alert(eq("WITHDRAW_AMOUNT_MISMATCH"), eq(AlertLevel.CRITICAL), anyString());
    }

    // ============================
    // 5.3 confirmWithdraw — null actualAmount → no comparison, confirm normally
    // ============================

    @Test
    void confirmWithdraw_nullActualAmount_noComparisonConfirmNormally() throws Exception {
        stubLock();

        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(1000L)
                .feeAmount(10L)
                .amountExponent(2)
                .txHash("0xtxhash001")
                .status(WithdrawStatus.PENDING_CONFIRM.getCode())
                .build();
        doReturn(record).when(withdrawRecordMapper).selectById(1L);
        doReturn(1).when(withdrawRecordMapper).updateById(any());

        withdrawService.confirmWithdraw(1L, "0xtxhash001", 12345L, null);

        assertEquals(WithdrawStatus.SUCCESS.getCode(), record.getStatus());
        verify(alertService, never()).alert(anyString(), any(AlertLevel.class), anyString());
    }
}
