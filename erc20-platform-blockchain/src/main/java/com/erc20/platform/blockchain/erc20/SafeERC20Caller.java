package com.erc20.platform.blockchain.erc20;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class SafeERC20Caller {

    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
    private static final int DEFAULT_DECIMALS = 18;

    private final Web3j web3j;

    public SafeERC20Caller(Web3j web3j) {
        this.web3j = web3j;
    }

    public BigInteger safeBalanceOf(String contract, String owner) {
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(owner)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );
        String result = ethCall(contract, function);
        List<org.web3j.abi.datatypes.Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
        return ((Uint256) decoded.get(0)).getValue();
    }

    public int safeDecimals(String contract) {
        try {
            Function function = new Function(
                    "decimals",
                    Collections.emptyList(),
                    Collections.singletonList(new TypeReference<Uint8>() {})
            );
            String result = ethCall(contract, function);
            List<org.web3j.abi.datatypes.Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
            if (!decoded.isEmpty()) {
                return ((Uint8) decoded.get(0)).getValue().intValue();
            }
            return decodeBytes32AsInt(result);
        } catch (Exception e) {
            log.warn("Failed to read decimals for {}, using default {}: {}", contract, DEFAULT_DECIMALS, e.getMessage());
            return DEFAULT_DECIMALS;
        }
    }

    public String safeSymbol(String contract) {
        Function function = new Function(
                "symbol",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {})
        );
        String result = ethCall(contract, function);
        try {
            List<org.web3j.abi.datatypes.Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
            if (!decoded.isEmpty()) {
                String value = ((Utf8String) decoded.get(0)).getValue();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception e) {
            // fall through to bytes32 decode
        }
        return decodeBytes32AsString(result);
    }

    public String safeName(String contract) {
        Function function = new Function(
                "name",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {})
        );
        String result = ethCall(contract, function);
        try {
            List<org.web3j.abi.datatypes.Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
            if (!decoded.isEmpty()) {
                String value = ((Utf8String) decoded.get(0)).getValue();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception e) {
            // fall through to bytes32 decode
        }
        return decodeBytes32AsString(result);
    }

    public CompletableFuture<TransactionReceipt> safeTransfer(String contract, String to, BigInteger amount) {
        throw new UnsupportedOperationException("safeTransfer requires transaction signing, not yet implemented");
    }

    public CompletableFuture<TransactionReceipt> safeApprove(String contract, String spender, BigInteger amount) {
        throw new UnsupportedOperationException("safeApprove requires transaction signing, not yet implemented");
    }

    private String ethCall(String contract, Function function) {
        String encodedFunction = FunctionEncoder.encode(function);
        try {
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(ZERO_ADDRESS, contract, encodedFunction),
                    DefaultBlockParameterName.LATEST
            ).send();
            if (response.hasError()) {
                throw new RuntimeException("eth_call error: " + response.getError().getMessage());
            }
            return response.getValue();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("eth_call failed", e);
        }
    }

    private int decodeBytes32AsInt(String hexValue) {
        byte[] bytes = Numeric.hexStringToByteArray(hexValue);
        return new BigInteger(1, bytes).intValue();
    }

    private String decodeBytes32AsString(String hexValue) {
        String cleaned = Numeric.cleanHexPrefix(hexValue);
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        byte[] bytes = Numeric.hexStringToByteArray(cleaned);
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) {
            length--;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
