package com.erc20.platform.blockchain.adapter.rpc;

import com.erc20.platform.blockchain.adapter.model.CallResult;
import com.erc20.platform.blockchain.erc20.ChainCallException;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

@Component
public class ERC20RpcClient {

    private static final String EXECUTION_REVERTED = "execution reverted";

    private final Web3j web3j;
    private final ReturnValueDecoder decoder;

    public ERC20RpcClient(Web3j web3j, ReturnValueDecoder decoder) {
        this.web3j = web3j;
        this.decoder = decoder;
    }

    public CallResult preCheckTransfer(String contract, String from, String to, BigInteger amount) {
        Function function = new Function(
                "transfer",
                Arrays.asList(new Address(to), new Uint256(amount)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeEthCall(contract, from, function);
    }

    public CallResult preCheckApprove(String contract, String owner, String spender, BigInteger amount) {
        Function function = new Function(
                "approve",
                Arrays.asList(new Address(spender), new Uint256(amount)),
                Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeEthCall(contract, owner, function);
    }

    private CallResult executeEthCall(String contract, String from, Function function) {
        String encodedFunction = FunctionEncoder.encode(function);
        try {
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(from, contract, encodedFunction),
                    DefaultBlockParameterName.LATEST
            ).send();

            if (response.hasError()) {
                String errorMessage = response.getError().getMessage();
                if (errorMessage != null && errorMessage.contains(EXECUTION_REVERTED)) {
                    return CallResult.reverted();
                }
                throw new ChainCallException(contract, errorMessage);
            }

            return decoder.decodeBoolReturn(response.getValue());
        } catch (IOException e) {
            throw new ChainCallException(contract, e);
        }
    }
}
