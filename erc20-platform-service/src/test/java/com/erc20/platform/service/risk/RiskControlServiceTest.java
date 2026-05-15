package com.erc20.platform.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.risk.rule.AddressBlacklistRule;
import com.erc20.platform.service.risk.rule.AmountLimitRule;
import com.erc20.platform.service.risk.rule.FrequencyRule;
import com.erc20.platform.service.risk.rule.LargeAmountRule;
import com.erc20.platform.service.risk.rule.NewAddressRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskControlServiceTest {

    @Mock private AddressBlacklistService blacklistService;
    @Mock private WithdrawLimitService limitService;
    @Mock private RedissonClient redissonClient;
    @Mock private WithdrawRecordMapper withdrawRecordMapper;
    @Mock private RAtomicLong hourlyCounter;

    private RiskProperties riskProperties;
    private RiskControlService riskControlService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;

    @BeforeEach
    void setUp() {
        riskProperties = new RiskProperties();
        riskProperties.setAutoPassMaxAmount(10000L);
        riskProperties.setDailyLimit(100000L);
        riskProperties.setHourlyMaxCount(5);
        riskProperties.setNewAddressReview(true);
        riskProperties.setLargeAmountThreshold(50000L);

        List<RiskRule> rules = Arrays.<RiskRule>asList(
                new AddressBlacklistRule(blacklistService),
                new AmountLimitRule(riskProperties, limitService),
                new FrequencyRule(riskProperties, redissonClient),
                new NewAddressRule(riskProperties, withdrawRecordMapper),
                new LargeAmountRule(riskProperties)
        );

        riskControlService = new RiskControlService(rules);
    }

    // --- 1.1 blacklisted address → REJECT ---

    @Test
    void checkWithdraw_blacklistedAddress_rejectsWithReason() {
        WithdrawRecord record = buildRecord("0xblacklisted", 5000L);
        doReturn(true).when(blacklistService).isBlacklisted("0xblacklisted");

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.REJECT, result.getStatus());
        assertNotNull(result.getReason());
        assertTrue(result.getReason().toLowerCase().contains("blacklist"));
    }

    // --- 1.2 amount below auto-pass, not blacklisted, not new → AUTO_PASS ---

    @Test
    void checkWithdraw_smallAmountNormalAddress_autoPass() {
        WithdrawRecord record = buildRecord("0xnormal", 5000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnormal");
        doReturn(true).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 5000L);
        doReturn(hourlyCounter).when(redissonClient).getAtomicLong(anyString());
        doReturn(2L).when(hourlyCounter).get();
        doReturn(WithdrawRecord.builder().id(1L).build())
                .when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.AUTO_PASS, result.getStatus());
    }

    // --- 1.3 amount above auto-pass but below daily limit → NEED_MANUAL_REVIEW ---

    @Test
    void checkWithdraw_amountAboveAutoPassBelowDailyLimit_needsManualReview() {
        WithdrawRecord record = buildRecord("0xnormal", 20000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnormal");
        doReturn(true).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 20000L);
        doReturn(hourlyCounter).when(redissonClient).getAtomicLong(anyString());
        doReturn(2L).when(hourlyCounter).get();
        doReturn(WithdrawRecord.builder().id(1L).build())
                .when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.NEED_MANUAL_REVIEW, result.getStatus());
    }

    // --- 1.4 amount exceeds daily limit → REJECT ---

    @Test
    void checkWithdraw_exceedsDailyLimit_rejects() {
        WithdrawRecord record = buildRecord("0xnormal", 20000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnormal");
        doReturn(false).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 20000L);

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.REJECT, result.getStatus());
        assertNotNull(result.getReason());
    }

    // --- 1.5 hourly frequency exceeded → NEED_MANUAL_REVIEW ---

    @Test
    void checkWithdraw_hourlyFrequencyExceeded_needsManualReview() {
        WithdrawRecord record = buildRecord("0xnormal", 5000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnormal");
        doReturn(true).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 5000L);
        doReturn(hourlyCounter).when(redissonClient).getAtomicLong(anyString());
        doReturn(5L).when(hourlyCounter).get();

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.NEED_MANUAL_REVIEW, result.getStatus());
    }

    // --- 1.6 first-time address with new-address-review=true → NEED_MANUAL_REVIEW ---

    @Test
    void checkWithdraw_newAddress_needsManualReview() {
        WithdrawRecord record = buildRecord("0xnewaddr", 5000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnewaddr");
        doReturn(true).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 5000L);
        doReturn(hourlyCounter).when(redissonClient).getAtomicLong(anyString());
        doReturn(2L).when(hourlyCounter).get();
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.NEED_MANUAL_REVIEW, result.getStatus());
    }

    // --- 1.7 amount >= large-amount-threshold → NEED_MANUAL_REVIEW ---

    @Test
    void checkWithdraw_largeAmount_needsManualReview() {
        WithdrawRecord record = buildRecord("0xnormal", 50000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnormal");
        doReturn(true).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 50000L);
        doReturn(hourlyCounter).when(redissonClient).getAtomicLong(anyString());
        doReturn(2L).when(hourlyCounter).get();
        doReturn(WithdrawRecord.builder().id(1L).build())
                .when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.NEED_MANUAL_REVIEW, result.getStatus());
    }

    // --- 1.8 multiple rules triggered — first REJECT wins, NEED_MANUAL_REVIEW accumulated ---

    @Test
    void checkWithdraw_multipleManualReviewRules_accumulates() {
        WithdrawRecord record = buildRecord("0xnewaddr", 50000L);
        doReturn(false).when(blacklistService).isBlacklisted("0xnewaddr");
        doReturn(true).when(limitService).checkAndAccumulate(USER_ID, TOKEN_ID, 50000L);
        doReturn(hourlyCounter).when(redissonClient).getAtomicLong(anyString());
        doReturn(5L).when(hourlyCounter).get();
        doReturn(null).when(withdrawRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.NEED_MANUAL_REVIEW, result.getStatus());
        assertNotNull(result.getReason());
        // Multiple reasons should be accumulated
        String reason = result.getReason();
        assertTrue(reason.contains(";") || reason.contains(",") || reason.split("\n").length > 1,
                "Expected multiple reasons accumulated, got: " + reason);
    }

    @Test
    void checkWithdraw_blacklistedWithOtherViolations_rejectWinsImmediately() {
        WithdrawRecord record = buildRecord("0xblacklisted", 50000L);
        doReturn(true).when(blacklistService).isBlacklisted("0xblacklisted");

        RiskResult result = riskControlService.checkWithdraw(record);

        assertEquals(RiskStatus.REJECT, result.getStatus());
        verifyNoInteractions(limitService);
    }

    private WithdrawRecord buildRecord(String toAddress, long amount) {
        return WithdrawRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .toAddress(toAddress)
                .amount(amount)
                .amountExponent(2)
                .build();
    }
}
