package com.erc20.platform.blockchain.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AdminEventMonitor {

    public static final String PAUSED_EVENT_TOPIC =
            "0x62e78cea01bee320cd4e420270b5ea74000d11b0c9f74754ebdbfc544b05a258";

    public static final String UPGRADED_EVENT_TOPIC =
            "0xbc7cd75a20ee27fd9adebab32041f755214dbc6bffa90cc0225b39da2e5c2d3b";

    private final Web3j web3j;
    private final TokenConfigMapper tokenConfigMapper;
    private final AlertService alertService;

    public AdminEventMonitor(Web3j web3j,
                             TokenConfigMapper tokenConfigMapper,
                             AlertService alertService) {
        this.web3j = web3j;
        this.tokenConfigMapper = tokenConfigMapper;
        this.alertService = alertService;
    }

    public void checkAdminEvents(long fromBlock, long toBlock) {
        List<TokenConfig> tokens = tokenConfigMapper.selectList(
                new LambdaQueryWrapper<TokenConfig>()
                        .eq(TokenConfig::getEnabled, 1));
        if (tokens.isEmpty()) {
            return;
        }

        Map<String, TokenConfig> addressToToken = new HashMap<String, TokenConfig>();
        List<String> addresses = tokens.stream()
                .map(t -> {
                    addressToToken.put(t.getContractAddress().toLowerCase(), t);
                    return t.getContractAddress();
                })
                .collect(Collectors.toList());

        try {
            EthFilter filter = new EthFilter(
                    new DefaultBlockParameterNumber(BigInteger.valueOf(fromBlock)),
                    new DefaultBlockParameterNumber(BigInteger.valueOf(toBlock)),
                    addresses);

            EthLog ethLog = web3j.ethGetLogs(filter).send();
            if (ethLog.getLogs() == null || ethLog.getLogs().isEmpty()) {
                return;
            }

            for (EthLog.LogResult<?> logResult : ethLog.getLogs()) {
                if (logResult instanceof EthLog.LogObject) {
                    Log logEntry = ((EthLog.LogObject) logResult).get();
                    processLog(logEntry, addressToToken);
                }
            }
        } catch (Exception e) {
            log.error("Failed to check admin events for blocks {}-{}", fromBlock, toBlock, e);
        }
    }

    private void processLog(Log logEntry, Map<String, TokenConfig> addressToToken) {
        List<String> topics = logEntry.getTopics();
        if (topics == null || topics.isEmpty()) {
            return;
        }

        String contractAddr = logEntry.getAddress().toLowerCase();
        TokenConfig token = addressToToken.get(contractAddr);
        if (token == null) {
            return;
        }

        String topic0 = topics.get(0);
        long blockNumber = logEntry.getBlockNumber().longValue();

        if (PAUSED_EVENT_TOPIC.equalsIgnoreCase(topic0)) {
            String content = "Token " + token.getTokenSymbol() + " paused at block " + blockNumber;
            alertService.alert("TOKEN_ADMIN_EVENT", AlertLevel.CRITICAL, content);
        } else if (UPGRADED_EVENT_TOPIC.equalsIgnoreCase(topic0)) {
            String newImpl = "unknown";
            if (topics.size() > 1) {
                String implTopic = topics.get(1);
                String hex = implTopic.startsWith("0x") ? implTopic.substring(2) : implTopic;
                if (hex.length() >= 40) {
                    newImpl = "0x" + hex.substring(hex.length() - 40).toLowerCase();
                }
            }
            String content = "Token " + token.getTokenSymbol() + " upgraded to " + newImpl + " at block " + blockNumber;
            alertService.alert("TOKEN_ADMIN_EVENT", AlertLevel.CRITICAL, content);
        }
    }
}
