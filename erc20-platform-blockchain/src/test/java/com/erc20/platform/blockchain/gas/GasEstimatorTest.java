package com.erc20.platform.blockchain.gas;

import com.erc20.platform.common.exception.ContractRevertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthEstimateGas;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasEstimatorTest {

    @Mock
    private Web3j web3j;

    @Mock
    @SuppressWarnings("rawtypes")
    private Request estimateGasRequest;

    private GasProperties gasProperties;
    private GasEstimator estimator;

    private static final String CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String FROM = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TO = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final BigInteger AMOUNT = BigInteger.valueOf(1_000_000L);

    @BeforeEach
    void setUp() {
        gasProperties = new GasProperties();
        estimator = new GasEstimator(web3j, gasProperties);
    }

    @SuppressWarnings("unchecked")
    @Test
    void estimateERC20Transfer_normalEstimate_appliesBuffer() throws Exception {
        EthEstimateGas response = mock(EthEstimateGas.class);
        when(response.getAmountUsed()).thenReturn(BigInteger.valueOf(60000));
        when(estimateGasRequest.send()).thenReturn(response);
        when(web3j.ethEstimateGas(any())).thenReturn(estimateGasRequest);

        BigInteger result = estimator.estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT);

        // 60000 * 1.2 = 72000
        assertEquals(BigInteger.valueOf(72000), result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void estimateERC20Transfer_estimateFails_returnsDefault80000() throws Exception {
        when(web3j.ethEstimateGas(any())).thenReturn(estimateGasRequest);
        when(estimateGasRequest.send()).thenThrow(new RuntimeException("execution reverted"));

        BigInteger result = estimator.estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT);

        assertEquals(BigInteger.valueOf(80000), result);
    }

    @Test
    void estimateEthTransfer_alwaysReturns21000() {
        BigInteger result = estimator.estimateEthTransfer();

        assertEquals(BigInteger.valueOf(21000), result);
    }

    // --- 7.1 response hasError with "execution reverted" → ContractRevertException ---

    @SuppressWarnings("unchecked")
    @Test
    void estimateERC20Transfer_responseHasRevertError_throwsContractRevertException() throws Exception {
        EthEstimateGas response = mock(EthEstimateGas.class);
        when(response.hasError()).thenReturn(true);
        Response.Error error = new Response.Error(-32000, "execution reverted: ERC20: transfer amount exceeds balance");
        when(response.getError()).thenReturn(error);
        when(estimateGasRequest.send()).thenReturn(response);
        when(web3j.ethEstimateGas(any())).thenReturn(estimateGasRequest);

        assertThrows(ContractRevertException.class, () ->
                estimator.estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT));
    }

    // --- 7.2 IOException → DEFAULT_ERC20_GAS fallback ---

    @SuppressWarnings("unchecked")
    @Test
    void estimateERC20Transfer_ioException_returnsDefault80000() throws Exception {
        when(web3j.ethEstimateGas(any())).thenReturn(estimateGasRequest);
        when(estimateGasRequest.send()).thenThrow(new IOException("Connection refused"));

        BigInteger result = estimator.estimateERC20Transfer(CONTRACT, FROM, TO, AMOUNT);

        assertEquals(BigInteger.valueOf(80000), result);
    }
}
