package com.erc20.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.common.enums.AddressStatus;
import com.erc20.platform.common.enums.DepositStatus;
import com.erc20.platform.common.enums.FlowType;
import com.erc20.platform.common.enums.TokenType;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.dal.mapper.UserAddressMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.domain.entity.UserAddress;
import com.erc20.platform.service.dto.AccountOperateRequest;
import com.erc20.platform.service.dto.TransferEventDTO;
import com.erc20.platform.service.monitoring.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock private DepositRecordMapper depositRecordMapper;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private UserAddressMapper userAddressMapper;
    @Mock private AccountService accountService;
    @Mock private AlertService alertService;
    @Mock private BusinessMetrics businessMetrics;

    private DepositService depositService;

    private static final String USER_ID = "user001";
    private static final Long TOKEN_ID = 1L;
    private static final String CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String PLATFORM_ADDRESS = "0xabc123def456abc123def456abc123def456abc1";
    private static final String EXTERNAL_ADDRESS = "0x9999999999999999999999999999999999999999";

    @BeforeEach
    void setUp() {
        depositService = new DepositService(depositRecordMapper, tokenConfigMapper,
                userAddressMapper, accountService, alertService, businessMetrics);
    }

    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private TokenConfig buildTokenConfig() {
        return TokenConfig.builder()
                .id(TOKEN_ID)
                .chainId(1)
                .contractAddress(CONTRACT_ADDRESS)
                .decimals(6)
                .amountExponent(2)
                .depositConfirmBlocks(12)
                .minDepositAmount(100L)
                .tokenType(TokenType.STANDARD.getCode())
                .enabled(1)
                .build();
    }

    private UserAddress buildPlatformAddress() {
        return UserAddress.builder()
                .id(10L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .address(PLATFORM_ADDRESS)
                .status(AddressStatus.BOUND.getCode())
                .build();
    }

    private TransferEventDTO buildTransferEvent(long amount) {
        return TransferEventDTO.builder()
                .contractAddress(CONTRACT_ADDRESS)
                .from(EXTERNAL_ADDRESS)
                .to(PLATFORM_ADDRESS)
                .value(BigInteger.valueOf(amount))
                .txHash("0xtx001")
                .blockNumber(100L)
                .blockHash("0xblockhash001")
                .logIndex(0)
                .build();
    }

    // --- 3.1 TransferEvent to platform address for registered token → DepositRecord created ---

    @Test
    void onTransferEvent_validDeposit_createsRecordWithConfirmingStatus() {
        // 1000000 chain amount = 10000 min-unit (decimals=6, exponent=2, diff=4, so /10000)
        TransferEventDTO event = buildTransferEvent(1000000L);

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(buildPlatformAddress()).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(depositRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).insert(any(DepositRecord.class));

        depositService.onTransferEvent(event);

        ArgumentCaptor<DepositRecord> captor = ArgumentCaptor.forClass(DepositRecord.class);
        verify(depositRecordMapper).insert(captor.capture());
        DepositRecord record = captor.getValue();
        assertEquals(DepositStatus.CONFIRMING.getCode(), record.getStatus());
        assertEquals(USER_ID, record.getUserId());
        assertEquals(TOKEN_ID, record.getTokenId());
        assertEquals(100L, record.getBlockNumber().longValue());
        assertEquals(12, record.getRequiredConfirmations().intValue());
        assertEquals(0, record.getCredited().intValue());
    }

    // --- 3.2 to-address is NOT platform address → no record created ---

    @Test
    void onTransferEvent_nonPlatformAddress_skips() {
        TransferEventDTO event = buildTransferEvent(1000000L);
        event.setTo("0x0000000000000000000000000000000000000000");

        doReturn(null).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));

        depositService.onTransferEvent(event);

        verify(depositRecordMapper, never()).insert(any());
    }

    // --- 3.3 contract not in token_config → no record created ---

    @Test
    void onTransferEvent_unknownContract_skips() {
        TransferEventDTO event = buildTransferEvent(1000000L);

        doReturn(buildPlatformAddress()).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        depositService.onTransferEvent(event);

        verify(depositRecordMapper, never()).insert(any());
    }

    // --- 3.4 same TransferEvent twice (same txHash+logIndex) → only one record ---

    @Test
    void onTransferEvent_duplicateEvent_skips() {
        TransferEventDTO event = buildTransferEvent(1000000L);

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(buildPlatformAddress()).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));

        DepositRecord existing = DepositRecord.builder().id(1L).idempotentKey("1_0xtx001_0").build();
        doReturn(existing).when(depositRecordMapper).selectOne(any(LambdaQueryWrapper.class));

        depositService.onTransferEvent(event);

        verify(depositRecordMapper, never()).insert(any());
    }

    // --- 3.5 amount < min_deposit_amount → status BELOW_MINIMUM ---

    @Test
    void onTransferEvent_belowMinimum_createsRecordWithBelowMinimumStatus() {
        // min_deposit_amount=100, send 99 chain units → 99/(10^4)=0 min-unit? No...
        // Actually: decimals=6, exponent=2, so diff=4. chainAmount=50 → 50/10000=0.005 → rounds to 0.
        // Let's use a more realistic scenario: min=100 min-unit, send 500000 chain amount → 50 min-unit < 100
        TransferEventDTO event = buildTransferEvent(500000L);

        doReturn(buildTokenConfig()).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(buildPlatformAddress()).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(depositRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).insert(any(DepositRecord.class));

        depositService.onTransferEvent(event);

        ArgumentCaptor<DepositRecord> captor = ArgumentCaptor.forClass(DepositRecord.class);
        verify(depositRecordMapper).insert(captor.capture());
        DepositRecord record = captor.getValue();
        assertEquals(DepositStatus.BELOW_MINIMUM.getCode(), record.getStatus());
    }

    // --- 3.6 creditDeposit → balance increased, status=SUCCESS, credited=true ---

    @Test
    void creditDeposit_confirmingDeposit_creditsAndUpdatesStatus() {
        DepositRecord deposit = DepositRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(10000L)
                .amountExponent(2)
                .status(DepositStatus.CONFIRMING.getCode())
                .credited(0)
                .build();
        doReturn(deposit).when(depositRecordMapper).selectById(1L);
        doReturn(1).when(depositRecordMapper).updateById(any(DepositRecord.class));

        depositService.creditDeposit(1L);

        verify(accountService).increaseAvailable(any(AccountOperateRequest.class));

        ArgumentCaptor<DepositRecord> captor = ArgumentCaptor.forClass(DepositRecord.class);
        verify(depositRecordMapper).updateById(captor.capture());
        DepositRecord updated = captor.getValue();
        assertEquals(DepositStatus.SUCCESS.getCode(), updated.getStatus());
        assertEquals(1, updated.getCredited().intValue());
    }

    // --- 3.7 creditDeposit called twice → second call is no-op ---

    @Test
    void creditDeposit_alreadyCredited_skips() {
        DepositRecord deposit = DepositRecord.builder()
                .id(1L)
                .userId(USER_ID)
                .tokenId(TOKEN_ID)
                .amount(10000L)
                .amountExponent(2)
                .status(DepositStatus.SUCCESS.getCode())
                .credited(1)
                .build();
        doReturn(deposit).when(depositRecordMapper).selectById(1L);

        depositService.creditDeposit(1L);

        verify(accountService, never()).increaseAvailable(any());
        verify(depositRecordMapper, never()).updateById(any());
    }

    // --- 8.1 Mint event (from=zero address) → skipped ---

    @Test
    void onTransferEvent_mintEvent_fromZeroAddress_skipped() {
        TransferEventDTO event = buildTransferEvent(1000000L);
        event.setFrom(ZERO_ADDRESS);

        depositService.onTransferEvent(event);

        verify(userAddressMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(tokenConfigMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(depositRecordMapper, never()).insert(any());
    }

    // --- 8.2 Non-STANDARD token type → skipped ---

    @Test
    void onTransferEvent_feeOnTransferToken_skipped() {
        TransferEventDTO event = buildTransferEvent(1000000L);

        TokenConfig feeToken = buildTokenConfig();
        feeToken.setTokenType(TokenType.FEE_ON_TRANSFER.getCode());

        doReturn(buildPlatformAddress()).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(feeToken).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));

        depositService.onTransferEvent(event);

        verify(depositRecordMapper, never()).insert(any());
    }

    // --- 8.3 Amount overflow → AMOUNT_OVERFLOW status + alert ---

    @Test
    void onTransferEvent_amountOverflow_createsRecordWithOverflowStatusAndAlert() {
        BigInteger hugeAmount = BigInteger.TEN.pow(32);
        TransferEventDTO event = TransferEventDTO.builder()
                .contractAddress(CONTRACT_ADDRESS)
                .from(EXTERNAL_ADDRESS)
                .to(PLATFORM_ADDRESS)
                .value(hugeAmount)
                .txHash("0xtx_overflow")
                .blockNumber(200L)
                .blockHash("0xblockhash_overflow")
                .logIndex(0)
                .build();

        TokenConfig tokenConfig = buildTokenConfig();
        tokenConfig.setDecimals(18);
        tokenConfig.setAmountExponent(6);

        doReturn(buildPlatformAddress()).when(userAddressMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(tokenConfig).when(tokenConfigMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(null).when(depositRecordMapper).selectOne(any(LambdaQueryWrapper.class));
        doReturn(1).when(depositRecordMapper).insert(any(DepositRecord.class));

        depositService.onTransferEvent(event);

        ArgumentCaptor<DepositRecord> captor = ArgumentCaptor.forClass(DepositRecord.class);
        verify(depositRecordMapper).insert(captor.capture());
        DepositRecord record = captor.getValue();
        assertEquals(DepositStatus.AMOUNT_OVERFLOW.getCode(), record.getStatus());
        assertEquals(0L, record.getAmount().longValue());

        verify(alertService).alert(eq("DEPOSIT_OVERFLOW"), eq(AlertLevel.CRITICAL),
                contains("0xtx_overflow"), eq("0xtx_overflow"));
    }
}
