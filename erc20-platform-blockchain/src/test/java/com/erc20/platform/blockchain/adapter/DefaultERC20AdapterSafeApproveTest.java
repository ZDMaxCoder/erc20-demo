package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.exception.TokenAdmissionRejectedException;
import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.wallet.SafeTransferExecutor;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultERC20AdapterSafeApproveTest {

    private static final String OWNER = "0x1111111111111111111111111111111111111111";
    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String SPENDER = "0x2222222222222222222222222222222222222222";
    private static final BigInteger AMOUNT = new BigInteger("1000000");
    private static final String TX_HASH_ZERO = "0xaaaa";
    private static final String TX_HASH_FINAL = "0xbbbb";

    @Mock
    private SafeERC20Caller safeERC20Caller;
    @Mock
    private TokenAdmissionGateway tokenAdmissionGateway;
    @Mock
    private SafeTransferExecutor safeTransferExecutor;
    @Mock
    private TransferConfirmer transferConfirmer;
    @Mock
    private TokenRiskProfileRegistry tokenRiskProfileRegistry;
    @Mock
    private TokenMetadataCache tokenMetadataCache;

    private DefaultERC20Adapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultERC20Adapter(safeERC20Caller, tokenAdmissionGateway,
                safeTransferExecutor, transferConfirmer, tokenRiskProfileRegistry, tokenMetadataCache);
    }

    @Test
    void safeApprove_standardToken_delegatesToExecutorDirectly() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(EnumSet.of(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(profile);
        when(safeTransferExecutor.executeApprove(OWNER, CONTRACT, SPENDER, AMOUNT))
                .thenReturn(TX_HASH_FINAL);

        String result = adapter.safeApprove(OWNER, CONTRACT, SPENDER, AMOUNT);

        assertEquals(TX_HASH_FINAL, result);
        verify(tokenAdmissionGateway).checkAdmission(CONTRACT, "approve");
        verify(safeTransferExecutor).executeApprove(OWNER, CONTRACT, SPENDER, AMOUNT);
        verifyNoMoreInteractions(safeTransferExecutor);
    }

    @Test
    void safeApprove_approveRaceConditionToken_resetsToZeroThenApproves() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(EnumSet.of(TokenCapability.APPROVE_RACE_CONDITION))
                .riskLevel(RiskLevel.MEDIUM)
                .admissionPassed(true)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(profile);
        when(safeTransferExecutor.executeApprove(OWNER, CONTRACT, SPENDER, BigInteger.ZERO))
                .thenReturn(TX_HASH_ZERO);
        when(safeTransferExecutor.executeApprove(OWNER, CONTRACT, SPENDER, AMOUNT))
                .thenReturn(TX_HASH_FINAL);

        String result = adapter.safeApprove(OWNER, CONTRACT, SPENDER, AMOUNT);

        assertEquals(TX_HASH_FINAL, result);
        InOrder inOrder = inOrder(safeTransferExecutor);
        inOrder.verify(safeTransferExecutor).executeApprove(OWNER, CONTRACT, SPENDER, BigInteger.ZERO);
        inOrder.verify(safeTransferExecutor).executeApprove(OWNER, CONTRACT, SPENDER, AMOUNT);
    }

    @Test
    void safeApprove_approveRaceConditionButAmountZero_skipsResetStep() {
        TokenRiskProfile profile = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(EnumSet.of(TokenCapability.APPROVE_RACE_CONDITION))
                .riskLevel(RiskLevel.MEDIUM)
                .admissionPassed(true)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(profile);
        when(safeTransferExecutor.executeApprove(OWNER, CONTRACT, SPENDER, BigInteger.ZERO))
                .thenReturn(TX_HASH_ZERO);

        String result = adapter.safeApprove(OWNER, CONTRACT, SPENDER, BigInteger.ZERO);

        assertEquals(TX_HASH_ZERO, result);
        verify(safeTransferExecutor, times(1)).executeApprove(OWNER, CONTRACT, SPENDER, BigInteger.ZERO);
    }

    @Test
    void safeApprove_rejectedToken_throwsTokenAdmissionRejectedException() {
        doThrow(new TokenAdmissionRejectedException(CONTRACT, "REBASING token"))
                .when(tokenAdmissionGateway).checkAdmission(CONTRACT, "approve");

        assertThrows(TokenAdmissionRejectedException.class,
                () -> adapter.safeApprove(OWNER, CONTRACT, SPENDER, AMOUNT));

        verify(tokenAdmissionGateway).checkAdmission(CONTRACT, "approve");
        verifyNoInteractions(safeTransferExecutor);
        verifyNoInteractions(tokenRiskProfileRegistry);
    }
}
