package com.erc20.platform.blockchain.wallet;

import com.erc20.platform.blockchain.gas.GasPrice;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.RawTransaction;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

@Component
public class TransactionBuilder {

    public RawTransaction buildERC20Transfer(long chainId, long nonce, GasPrice gasPrice,
                                             BigInteger gasLimit, String contractAddress,
                                             String to, BigInteger amount) {
        Function function = new Function(
                "transfer",
                Arrays.asList(new Address(to), new Uint256(amount)),
                Collections.<TypeReference<?>>emptyList()
        );
        String encodedData = FunctionEncoder.encode(function);

        if (gasPrice.isEip1559()) {
            return RawTransaction.createTransaction(
                    chainId,
                    BigInteger.valueOf(nonce),
                    gasLimit,
                    contractAddress,
                    BigInteger.ZERO,
                    encodedData,
                    gasPrice.getMaxPriorityFeePerGas(),
                    gasPrice.getMaxFeePerGas()
            );
        }
        return RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice.getGasPrice(),
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                encodedData
        );
    }

    public RawTransaction buildERC20Approve(long chainId, long nonce, GasPrice gasPrice,
                                             BigInteger gasLimit, String contractAddress,
                                             String spender, BigInteger amount) {
        Function function = new Function(
                "approve",
                Arrays.asList(new Address(spender), new Uint256(amount)),
                Collections.<TypeReference<?>>emptyList()
        );
        String encodedData = FunctionEncoder.encode(function);

        if (gasPrice.isEip1559()) {
            return RawTransaction.createTransaction(
                    chainId,
                    BigInteger.valueOf(nonce),
                    gasLimit,
                    contractAddress,
                    BigInteger.ZERO,
                    encodedData,
                    gasPrice.getMaxPriorityFeePerGas(),
                    gasPrice.getMaxFeePerGas()
            );
        }
        return RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice.getGasPrice(),
                gasLimit,
                contractAddress,
                BigInteger.ZERO,
                encodedData
        );
    }

    public RawTransaction buildEthTransfer(long chainId, long nonce, GasPrice gasPrice,
                                           BigInteger gasLimit, String to, BigInteger amountWei) {
        if (gasPrice.isEip1559()) {
            return RawTransaction.createTransaction(
                    chainId,
                    BigInteger.valueOf(nonce),
                    gasLimit,
                    to,
                    amountWei,
                    "",
                    gasPrice.getMaxPriorityFeePerGas(),
                    gasPrice.getMaxFeePerGas()
            );
        }
        return RawTransaction.createEtherTransaction(
                BigInteger.valueOf(nonce),
                gasPrice.getGasPrice(),
                gasLimit,
                to,
                amountWei
        );
    }
}
