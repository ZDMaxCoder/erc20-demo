package com.erc20.platform.blockchain.erc20;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ERC20TransferEventParser {

    public static final String TRANSFER_EVENT_SIGNATURE =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    public Optional<TransferEvent> parse(Log logEntry, String contractAddress) {
        try {
            List<String> topics = logEntry.getTopics();
            if (topics == null || topics.isEmpty()) {
                return Optional.empty();
            }

            if (!TRANSFER_EVENT_SIGNATURE.equalsIgnoreCase(topics.get(0))) {
                return Optional.empty();
            }

            if (topics.size() < 3) {
                return Optional.empty();
            }

            String data = logEntry.getData();
            if (data == null || data.length() < 66) {
                return Optional.empty();
            }

            String from = extractAddress(topics.get(1));
            String to = extractAddress(topics.get(2));
            BigInteger value = new BigInteger(data.substring(2, 66), 16);

            return Optional.of(TransferEvent.builder()
                    .contractAddress(contractAddress.toLowerCase())
                    .from(from)
                    .to(to)
                    .value(value)
                    .txHash(logEntry.getTransactionHash())
                    .blockNumber(logEntry.getBlockNumber().longValue())
                    .logIndex(logEntry.getLogIndex().intValue())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to parse Transfer event: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<TransferEvent> parseFromReceipt(TransactionReceipt receipt, String contractAddress) {
        if (receipt.getLogs() == null) {
            return Collections.emptyList();
        }

        String normalizedContract = contractAddress.toLowerCase();
        List<TransferEvent> events = new ArrayList<>();
        for (Log logEntry : receipt.getLogs()) {
            if (normalizedContract.equalsIgnoreCase(logEntry.getAddress())) {
                Optional<TransferEvent> event = parse(logEntry, contractAddress);
                if (event.isPresent()) {
                    events.add(event.get());
                }
            }
        }
        return events;
    }

    private String extractAddress(String topic) {
        String hex = Numeric.cleanHexPrefix(topic);
        return "0x" + hex.substring(24).toLowerCase();
    }
}
