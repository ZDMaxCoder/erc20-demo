package com.erc20.platform.blockchain.gas;

import com.erc20.platform.common.exception.ContractRevertException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthEstimateGas;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Component
public class GasEstimator {

    private static final BigInteger ETH_TRANSFER_GAS = BigInteger.valueOf(21000);
    private static final BigInteger DEFAULT_ERC20_GAS = BigInteger.valueOf(80000);

    private final Web3j web3j;
    private final GasProperties gasProperties;

    public GasEstimator(Web3j web3j, GasProperties gasProperties) {
        this.web3j = web3j;
        this.gasProperties = gasProperties;
    }

    public BigInteger estimateERC20Transfer(String contract, String from, String to, BigInteger amount) {
        try {
            Function function = new Function(
                    "transfer",
                    Arrays.asList(new Address(to), new Uint256(amount)),
                    Collections.<TypeReference<?>>emptyList()
            );
            String encodedFunction = FunctionEncoder.encode(function);

            Transaction tx = Transaction.createEthCallTransaction(from, contract, encodedFunction);
            EthEstimateGas response = web3j.ethEstimateGas(tx).send();

            if (response.hasError()) {
                String errorMessage = response.getError().getMessage();
                if (errorMessage != null && errorMessage.contains("execution reverted")) {
                    throw new ContractRevertException(errorMessage);
                }
                log.warn("Gas estimation error (non-revert): {}, using default {}", errorMessage, DEFAULT_ERC20_GAS);
                return DEFAULT_ERC20_GAS;
            }

            BigInteger estimate = response.getAmountUsed();
            int bufferPercent = gasProperties.getGasLimitBufferPercent();
            return estimate.multiply(BigInteger.valueOf(100 + bufferPercent)).divide(BigInteger.valueOf(100));
        } catch (ContractRevertException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Network error estimating gas for ERC20 transfer, using default {}", DEFAULT_ERC20_GAS, e);
            return DEFAULT_ERC20_GAS;
        } catch (Exception e) {
            log.warn("Failed to estimate gas for ERC20 transfer, using default {}", DEFAULT_ERC20_GAS, e);
            return DEFAULT_ERC20_GAS;
        }
    }

    public BigInteger estimateERC20Approve(String contract, String from, String spender, BigInteger amount) {
        try {
            Function function = new Function(
                    "approve",
                    Arrays.asList(new Address(spender), new Uint256(amount)),
                    Collections.<TypeReference<?>>emptyList()
            );
            String encodedFunction = FunctionEncoder.encode(function);

            Transaction tx = Transaction.createEthCallTransaction(from, contract, encodedFunction);
            EthEstimateGas response = web3j.ethEstimateGas(tx).send();

            if (response.hasError()) {
                String errorMessage = response.getError().getMessage();
                if (errorMessage != null && errorMessage.contains("execution reverted")) {
                    throw new ContractRevertException(errorMessage);
                }
                log.warn("Gas estimation error (non-revert) for approve: {}, using default {}", errorMessage, DEFAULT_ERC20_GAS);
                return DEFAULT_ERC20_GAS;
            }

            BigInteger estimate = response.getAmountUsed();
            int bufferPercent = gasProperties.getGasLimitBufferPercent();
            return estimate.multiply(BigInteger.valueOf(100 + bufferPercent)).divide(BigInteger.valueOf(100));
        } catch (ContractRevertException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Network error estimating gas for ERC20 approve, using default {}", DEFAULT_ERC20_GAS, e);
            return DEFAULT_ERC20_GAS;
        } catch (Exception e) {
            log.warn("Failed to estimate gas for ERC20 approve, using default {}", DEFAULT_ERC20_GAS, e);
            return DEFAULT_ERC20_GAS;
        }
    }

    public BigInteger estimateEthTransfer() {
        return ETH_TRANSFER_GAS;
    }
}
