package com.erc20.platform.blockchain.wallet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

@Slf4j
@Component
public class TransactionBroadcaster {

    private final Web3j web3j;

    public TransactionBroadcaster(Web3j web3j) {
        this.web3j = web3j;
    }

    public BroadcastResult broadcast(String signedTxHex) {
        try {
            EthSendTransaction response = web3j.ethSendRawTransaction(signedTxHex).send();

            if (response.hasError()) {
                return classifyError(response.getError().getMessage());
            }

            return BroadcastResult.success(response.getTransactionHash());
        } catch (Exception e) {
            log.error("Failed to broadcast transaction", e);
            return BroadcastResult.error(BroadcastErrorType.UNKNOWN, e.getMessage());
        }
    }

    private BroadcastResult classifyError(String errorMessage) {
        String msg = errorMessage.toLowerCase();

        if (msg.contains("nonce too low")) {
            return BroadcastResult.error(BroadcastErrorType.NONCE_TOO_LOW, errorMessage);
        }
        if (msg.contains("already known")) {
            return BroadcastResult.builder()
                    .success(true)
                    .errorType(BroadcastErrorType.ALREADY_KNOWN)
                    .errorMessage(errorMessage)
                    .build();
        }
        if (msg.contains("replacement transaction underpriced")) {
            return BroadcastResult.error(BroadcastErrorType.UNDERPRICED, errorMessage);
        }
        if (msg.contains("insufficient funds")) {
            return BroadcastResult.error(BroadcastErrorType.INSUFFICIENT_FUNDS, errorMessage);
        }

        return BroadcastResult.error(BroadcastErrorType.UNKNOWN, errorMessage);
    }
}
