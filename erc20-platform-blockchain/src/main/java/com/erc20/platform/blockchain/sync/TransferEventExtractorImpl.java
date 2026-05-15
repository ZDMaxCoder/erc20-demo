package com.erc20.platform.blockchain.sync;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erc20.platform.blockchain.config.Web3jProvider;
import com.erc20.platform.blockchain.erc20.ERC20TransferEventParser;
import com.erc20.platform.blockchain.erc20.TransferEvent;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TransferEventExtractorImpl implements TransferEventExtractor {

    private final Web3jProvider web3jProvider;
    private final TokenConfigMapper tokenConfigMapper;
    private final ERC20TransferEventParser parser;
    private final BlockSyncProperties properties;

    private volatile List<String> cachedContracts;
    private volatile long cacheRefreshTime;
    private static final long CACHE_TTL_MS = 60_000;

    public TransferEventExtractorImpl(Web3jProvider web3jProvider,
                                      TokenConfigMapper tokenConfigMapper,
                                      ERC20TransferEventParser parser,
                                      BlockSyncProperties properties) {
        this.web3jProvider = web3jProvider;
        this.tokenConfigMapper = tokenConfigMapper;
        this.parser = parser;
        this.properties = properties;
    }

    @Override
    public List<TransferEvent> extractFromBlock(long blockNumber, EthBlock.Block block) throws IOException {
        List<String> contracts = getRegisteredContracts();
        if (contracts.isEmpty()) {
            return Collections.emptyList();
        }

        DefaultBlockParameterNumber blockParam = new DefaultBlockParameterNumber(blockNumber);
        EthFilter filter = new EthFilter(blockParam, blockParam, contracts);
        filter.addSingleTopic(ERC20TransferEventParser.TRANSFER_EVENT_SIGNATURE);

        EthLog ethLog = web3jProvider.sendWithFailover(
                web3j -> web3j.ethGetLogs(filter).send());

        if (ethLog.getLogs() == null || ethLog.getLogs().isEmpty()) {
            return Collections.emptyList();
        }

        List<TransferEvent> events = new ArrayList<>();
        for (EthLog.LogResult<?> logResult : ethLog.getLogs()) {
            if (logResult instanceof EthLog.LogObject) {
                Log logEntry = ((EthLog.LogObject) logResult).get();
                String contractAddress = logEntry.getAddress();
                Optional<TransferEvent> event = parser.parse(logEntry, contractAddress);
                event.ifPresent(events::add);
            }
        }

        log.debug("Extracted {} transfer events from block {}", events.size(), blockNumber);
        return events;
    }

    private List<String> getRegisteredContracts() {
        long now = System.currentTimeMillis();
        if (cachedContracts == null || now - cacheRefreshTime > CACHE_TTL_MS) {
            List<TokenConfig> tokens = tokenConfigMapper.selectList(
                    new QueryWrapper<TokenConfig>()
                            .eq("enabled", 1)
                            .eq("chain_id", properties.getChainId()));
            cachedContracts = tokens.stream()
                    .map(t -> t.getContractAddress().toLowerCase())
                    .collect(Collectors.toList());
            cacheRefreshTime = now;
        }
        return cachedContracts;
    }
}
