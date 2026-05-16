package com.erc20.platform.blockchain.erc20;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ERC20TransferEventParserTest {

    private ERC20TransferEventParser parser;

    private static final String CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    private static final String FROM_ADDR = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TO_ADDR = "0x000000000000000000000000bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String FROM_CLEAN = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TO_CLEAN = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TX_HASH = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    @BeforeEach
    void setUp() {
        parser = new ERC20TransferEventParser();
    }

    @Test
    void parse_standardTransferEvent_returnsCorrectTransferEvent() {
        String valueHex = "0x0000000000000000000000000000000000000000000000000000000005f5e100";

        Log log = buildLog(
                CONTRACT,
                Arrays.asList(
                        ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE,
                        FROM_ADDR,
                        TO_ADDR
                ),
                valueHex,
                TX_HASH,
                "0x64",
                "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, CONTRACT);

        assertTrue(result.isPresent());
        TransferEvent event = result.get();
        assertEquals(CONTRACT, event.getContractAddress());
        assertEquals(FROM_CLEAN, event.getFrom());
        assertEquals(TO_CLEAN, event.getTo());
        assertEquals(new BigInteger("100000000"), event.getValue());
        assertEquals(TX_HASH, event.getTxHash());
        assertEquals(100L, event.getBlockNumber());
        assertEquals(0, event.getLogIndex());
    }

    @Test
    void parse_wrongTopicSignature_returnsEmpty() {
        String wrongSignature = "0x0000000000000000000000000000000000000000000000000000000000000000";

        Log log = buildLog(
                CONTRACT,
                Arrays.asList(wrongSignature, FROM_ADDR, TO_ADDR),
                "0x0000000000000000000000000000000000000000000000000000000005f5e100",
                TX_HASH, "0x64", "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, CONTRACT);

        assertFalse(result.isPresent());
    }

    @Test
    void parse_twoTopics_nonStandard_returnsEmpty() {
        Log log = buildLog(
                CONTRACT,
                Arrays.asList(
                        ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE,
                        FROM_ADDR
                ),
                "0x0000000000000000000000000000000000000000000000000000000005f5e100",
                TX_HASH, "0x64", "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, CONTRACT);

        assertFalse(result.isPresent());
    }

    @Test
    void parse_emptyData_returnsEmpty() {
        Log log = buildLog(
                CONTRACT,
                Arrays.asList(
                        ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE,
                        FROM_ADDR,
                        TO_ADDR
                ),
                "0x",
                TX_HASH, "0x64", "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, CONTRACT);

        assertFalse(result.isPresent());
    }

    @Test
    void parse_mixedCaseContract_normalizesToLowercase() {
        String mixedCase = "0xdAC17F958D2eE523a2206206994597C13D831Ec7";
        String valueHex = "0x0000000000000000000000000000000000000000000000000000000005f5e100";

        Log log = buildLog(
                mixedCase,
                Arrays.asList(
                        ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE,
                        FROM_ADDR,
                        TO_ADDR
                ),
                valueHex,
                TX_HASH, "0x64", "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, mixedCase);

        assertTrue(result.isPresent());
        assertEquals(mixedCase.toLowerCase(), result.get().getContractAddress());
    }

    @Test
    void parseFromReceipt_multipleLogsOnlyMatchingContractParsed() {
        String valueHex = "0x0000000000000000000000000000000000000000000000000000000005f5e100";
        String otherContract = "0x1111111111111111111111111111111111111111";

        Log matchingLog = buildLog(
                CONTRACT,
                Arrays.asList(ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE, FROM_ADDR, TO_ADDR),
                valueHex, TX_HASH, "0x64", "0x0"
        );

        Log nonMatchingLog = buildLog(
                otherContract,
                Arrays.asList(ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE, FROM_ADDR, TO_ADDR),
                valueHex, TX_HASH, "0x64", "0x1"
        );

        Log anotherMatchingLog = buildLog(
                CONTRACT,
                Arrays.asList(ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE, TO_ADDR, FROM_ADDR),
                valueHex, TX_HASH, "0x64", "0x2"
        );

        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogs(Arrays.asList(matchingLog, nonMatchingLog, anotherMatchingLog));

        List<TransferEvent> events = parser.parseFromReceipt(receipt, CONTRACT);

        assertEquals(2, events.size());
        assertEquals(FROM_CLEAN, events.get(0).getFrom());
        assertEquals(TO_CLEAN, events.get(1).getFrom());
    }

    // --- 14.3 data with 0x prefix + 64 hex chars → correct amount; extra trailing bytes ignored ---

    @Test
    void parse_dataWithExtraTrailingBytes_parsesFirst32BytesOnly() {
        String data = "0x0000000000000000000000000000000000000000000000000000000005f5e100"
                + "0000000000000000000000000000000000000000000000000000000000000001";

        Log log = buildLog(
                CONTRACT,
                Arrays.asList(ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE, FROM_ADDR, TO_ADDR),
                data, TX_HASH, "0x64", "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, CONTRACT);

        assertTrue(result.isPresent());
        assertEquals(new BigInteger("100000000"), result.get().getValue());
    }

    @Test
    void parse_standardData_0xPrefixAndExact64HexChars_correctAmount() {
        String data = "0x00000000000000000000000000000000000000000000000000000000000f4240";

        Log log = buildLog(
                CONTRACT,
                Arrays.asList(ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE, FROM_ADDR, TO_ADDR),
                data, TX_HASH, "0x64", "0x0"
        );

        Optional<TransferEvent> result = parser.parse(log, CONTRACT);

        assertTrue(result.isPresent());
        assertEquals(new BigInteger("1000000"), result.get().getValue());
    }

    private Log buildLog(String address, List<String> topics, String data,
                         String txHash, String blockNumber, String logIndex) {
        Log log = new Log();
        log.setAddress(address);
        log.setTopics(topics);
        log.setData(data);
        log.setTransactionHash(txHash);
        log.setBlockNumber(blockNumber);
        log.setLogIndex(logIndex);
        return log;
    }
}
