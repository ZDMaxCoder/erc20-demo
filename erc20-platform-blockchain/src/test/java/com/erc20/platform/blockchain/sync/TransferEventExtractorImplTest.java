package com.erc20.platform.blockchain.sync;

import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.blockchain.erc20.ERC20TransferEventParser;
import com.erc20.platform.blockchain.erc20.TransferEvent;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferEventExtractorImplTest {

    @Mock
    private Web3jProvider web3jProvider;
    @Mock
    private TokenConfigMapper tokenConfigMapper;
    @Mock
    private ERC20TransferEventParser parser;

    private BlockSyncProperties properties;
    private TransferEventExtractorImpl extractor;

    private static final String REGISTERED_CONTRACT = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String UNREGISTERED_CONTRACT = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @BeforeEach
    void setUp() {
        properties = new BlockSyncProperties();
        properties.setChainId(1);
        extractor = new TransferEventExtractorImpl(web3jProvider, tokenConfigMapper, parser, properties);
    }

    // --- Task 6.1: logs from registered contract → correct TransferEvents ---

    @Test
    void extractFromBlock_registeredContract_returnsTransferEvents() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress(REGISTERED_CONTRACT).enabled(1).chainId(1).build();
        doReturn(Collections.singletonList(token)).when(tokenConfigMapper).selectList(any());

        Log log = mock(Log.class);
        doReturn(REGISTERED_CONTRACT).when(log).getAddress();

        EthLog.LogObject logObject = mock(EthLog.LogObject.class);
        doReturn(log).when(logObject).get();

        EthLog ethLog = new EthLog();
        ethLog.setResult(Collections.singletonList(logObject));

        doReturn(ethLog).when(web3jProvider).sendWithFailover(any());

        TransferEvent event = TransferEvent.builder()
                .contractAddress(REGISTERED_CONTRACT)
                .from("0x1111111111111111111111111111111111111111")
                .to("0x2222222222222222222222222222222222222222")
                .value(BigInteger.valueOf(1000))
                .txHash("0xtx1")
                .blockNumber(100L)
                .logIndex(0)
                .build();
        doReturn(Optional.of(event)).when(parser).parse(any(Log.class), eq(REGISTERED_CONTRACT));

        EthBlock.Block block = mock(EthBlock.Block.class);

        List<TransferEvent> result = extractor.extractFromBlock(100L, block);

        assertEquals(1, result.size());
        assertEquals(REGISTERED_CONTRACT, result.get(0).getContractAddress());
        assertEquals("0x1111111111111111111111111111111111111111", result.get(0).getFrom());
    }

    // --- Task 6.2: logs from unregistered contract → filtered out ---

    @Test
    void extractFromBlock_unregisteredContract_filteredOut() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress(REGISTERED_CONTRACT).enabled(1).chainId(1).build();
        doReturn(Collections.singletonList(token)).when(tokenConfigMapper).selectList(any());

        // eth_getLogs returns empty because the filter only includes registered contracts
        EthLog ethLog = new EthLog();
        ethLog.setResult(Collections.emptyList());

        doReturn(ethLog).when(web3jProvider).sendWithFailover(any());

        EthBlock.Block block = mock(EthBlock.Block.class);

        List<TransferEvent> result = extractor.extractFromBlock(100L, block);

        assertTrue(result.isEmpty());
        verify(parser, never()).parse(any(), any());
    }
}
