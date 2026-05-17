package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.model.TokenRiskProfile;
import com.erc20.platform.blockchain.adapter.model.TransferOutcome;
import com.erc20.platform.blockchain.adapter.model.TransferResult;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.wallet.SafeTransferExecutor;
import com.erc20.platform.common.enums.RiskLevel;
import com.erc20.platform.common.enums.TokenCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultERC20AdapterConfirmAndGovernanceTest {

    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String TO_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TX_HASH = "0xabc123";

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
    void confirmTransfer_validParams_delegatesToTransferConfirmer() {
        BigInteger expectedAmount = new BigInteger("1000000");
        TransferResult expected = TransferResult.builder()
                .outcome(TransferOutcome.SUCCESS)
                .txHash(TX_HASH)
                .actualAmount(expectedAmount)
                .expectedAmount(expectedAmount)
                .build();
        when(transferConfirmer.confirm(TX_HASH, CONTRACT, expectedAmount, TO_ADDRESS))
                .thenReturn(expected);

        TransferResult result = adapter.confirmTransfer(TX_HASH, CONTRACT, expectedAmount, TO_ADDRESS);

        assertEquals(expected, result);
        verify(transferConfirmer).confirm(TX_HASH, CONTRACT, expectedAmount, TO_ADDRESS);
    }

    @Test
    void getTokenProfile_validContract_delegatesToRegistry() {
        TokenRiskProfile expected = TokenRiskProfile.builder()
                .contractAddress(CONTRACT)
                .capabilities(Collections.singleton(TokenCapability.STANDARD_RETURN))
                .riskLevel(RiskLevel.LOW)
                .admissionPassed(true)
                .build();
        when(tokenRiskProfileRegistry.getProfile(CONTRACT)).thenReturn(expected);

        TokenRiskProfile result = adapter.getTokenProfile(CONTRACT);

        assertEquals(expected, result);
        verify(tokenRiskProfileRegistry).getProfile(CONTRACT);
    }

    @Test
    void isTokenAdmitted_admittedToken_returnsTrue() {
        when(tokenAdmissionGateway.isAdmitted(CONTRACT)).thenReturn(true);

        boolean result = adapter.isTokenAdmitted(CONTRACT);

        assertTrue(result);
        verify(tokenAdmissionGateway).isAdmitted(CONTRACT);
    }

    @Test
    void isTokenAdmitted_rejectedToken_returnsFalse() {
        when(tokenAdmissionGateway.isAdmitted(CONTRACT)).thenReturn(false);

        boolean result = adapter.isTokenAdmitted(CONTRACT);

        assertFalse(result);
        verify(tokenAdmissionGateway).isAdmitted(CONTRACT);
    }
}
