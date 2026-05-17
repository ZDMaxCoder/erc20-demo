package com.erc20.platform.blockchain.adapter;

import com.erc20.platform.blockchain.adapter.exception.TokenAdmissionRejectedException;
import com.erc20.platform.blockchain.erc20.SafeERC20Caller;
import com.erc20.platform.blockchain.wallet.SafeTransferExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultERC20AdapterSafeTransferTest {

    private static final String FROM = "0x1111111111111111111111111111111111111111";
    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String TO = "0x2222222222222222222222222222222222222222";
    private static final BigInteger AMOUNT = new BigInteger("1000000");
    private static final String TX_HASH = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

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
    void safeTransfer_admittedToken_delegatesToExecutorAndReturnsTxHash() {
        doNothing().when(tokenAdmissionGateway).checkAdmission(CONTRACT, "transfer");
        when(safeTransferExecutor.executeTransfer(FROM, CONTRACT, TO, AMOUNT)).thenReturn(TX_HASH);

        String result = adapter.safeTransfer(FROM, CONTRACT, TO, AMOUNT);

        assertEquals(TX_HASH, result);
        verify(tokenAdmissionGateway).checkAdmission(CONTRACT, "transfer");
        verify(safeTransferExecutor).executeTransfer(FROM, CONTRACT, TO, AMOUNT);
    }

    @Test
    void safeTransfer_rejectedToken_throwsTokenAdmissionRejectedException() {
        doThrow(new TokenAdmissionRejectedException(CONTRACT, "REBASING token"))
                .when(tokenAdmissionGateway).checkAdmission(CONTRACT, "transfer");

        assertThrows(TokenAdmissionRejectedException.class,
                () -> adapter.safeTransfer(FROM, CONTRACT, TO, AMOUNT));

        verify(tokenAdmissionGateway).checkAdmission(CONTRACT, "transfer");
        verifyNoInteractions(safeTransferExecutor);
    }
}
