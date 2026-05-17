package com.erc20.platform.blockchain.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.blockchain.adapter.TokenMetadataCache;
import com.erc20.platform.blockchain.adapter.TokenRiskProfileRegistry;
import com.erc20.platform.common.enums.AlertLevel;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import com.erc20.platform.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventMonitorTest {

    @Mock private Web3j web3j;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private AlertService alertService;
    @Mock private TokenRiskProfileRegistry tokenRiskProfileRegistry;
    @Mock private TokenMetadataCache tokenMetadataCache;

    private AdminEventMonitor adminEventMonitor;

    @BeforeEach
    void setUp() {
        adminEventMonitor = new AdminEventMonitor(web3j, tokenConfigMapper, alertService, tokenRiskProfileRegistry, tokenMetadataCache);
    }

    // --- 13.1 Paused() event detected → CRITICAL alert + auto-disable ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_pausedEventDetected_disablesTokenAndRaisesCriticalAlert() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .tokenSymbol("USDT")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setAddress("0xdac17f958d2ee523a2206206994597c13d831ec7");
        logObject.setTopics(Collections.singletonList(AdminEventMonitor.PAUSED_EVENT_TOPIC));
        logObject.setBlockNumber("0x64");
        logObject.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(logObject);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        adminEventMonitor.checkAdminEvents(90, 100);

        assertEquals(0, token.getEnabled().intValue());
        verify(tokenConfigMapper).updateById(token);
        verify(alertService).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                contains("auto-disabled"));
        verify(alertService).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                contains("Token USDT paused at block 100"));
    }

    // --- 13.2 Upgraded(address) event detected → CRITICAL alert + auto-disable ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_upgradedEventDetected_disablesTokenAndRaisesCriticalAlertWithImplAddress() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .tokenSymbol("USDT")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        String newImplTopic = "0x000000000000000000000000abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setAddress("0xdac17f958d2ee523a2206206994597c13d831ec7");
        logObject.setTopics(Arrays.asList(AdminEventMonitor.UPGRADED_EVENT_TOPIC, newImplTopic));
        logObject.setBlockNumber("0xc8");
        logObject.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(logObject);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        adminEventMonitor.checkAdminEvents(180, 200);

        assertEquals(0, token.getEnabled().intValue());
        verify(tokenConfigMapper).updateById(token);
        verify(alertService).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                contains("auto-disabled"));
        verify(alertService).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                contains("Token USDT upgraded to 0xabcdefabcdefabcdefabcdefabcdefabcdefabcd at block 200"));
    }

    // --- 13.3 Already disabled → idempotent, no exception ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_alreadyDisabledByFirstEvent_secondEventIdempotentNoException() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
                .tokenSymbol("USDC")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        EthLog.LogObject pausedLog = new EthLog.LogObject();
        pausedLog.setAddress("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        pausedLog.setTopics(Collections.singletonList(AdminEventMonitor.PAUSED_EVENT_TOPIC));
        pausedLog.setBlockNumber("0x64");
        pausedLog.setData("0x");

        String newImplTopic = "0x0000000000000000000000001111111111111111111111111111111111111111";
        EthLog.LogObject upgradedLog = new EthLog.LogObject();
        upgradedLog.setAddress("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        upgradedLog.setTopics(Arrays.asList(AdminEventMonitor.UPGRADED_EVENT_TOPIC, newImplTopic));
        upgradedLog.setBlockNumber("0x64");
        upgradedLog.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(pausedLog);
        results.add(upgradedLog);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        assertDoesNotThrow(() -> adminEventMonitor.checkAdminEvents(90, 100));

        assertEquals(0, token.getEnabled().intValue());
        verify(tokenConfigMapper, times(2)).updateById(token);
        verify(alertService, times(2)).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                contains("auto-disabled"));
    }

    // --- 8.3 AdminEventMonitor invalidates TokenRiskProfileRegistry cache after disabling token ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_pausedEventDetected_invalidatesTokenRiskProfileCache() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .tokenSymbol("USDT")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setAddress("0xdac17f958d2ee523a2206206994597c13d831ec7");
        logObject.setTopics(Collections.singletonList(AdminEventMonitor.PAUSED_EVENT_TOPIC));
        logObject.setBlockNumber("0x64");
        logObject.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(logObject);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        adminEventMonitor.checkAdminEvents(90, 100);

        verify(tokenRiskProfileRegistry).invalidate("0xdac17f958d2ee523a2206206994597c13d831ec7");
    }

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_upgradedEventDetected_invalidatesTokenRiskProfileCache() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .tokenSymbol("USDT")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        String newImplTopic = "0x000000000000000000000000abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setAddress("0xdac17f958d2ee523a2206206994597c13d831ec7");
        logObject.setTopics(Arrays.asList(AdminEventMonitor.UPGRADED_EVENT_TOPIC, newImplTopic));
        logObject.setBlockNumber("0xc8");
        logObject.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(logObject);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        adminEventMonitor.checkAdminEvents(180, 200);

        verify(tokenRiskProfileRegistry).invalidate("0xdac17f958d2ee523a2206206994597c13d831ec7");
    }

    // --- 6.2 Paused event does NOT invalidate TokenMetadataCache ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_pausedEventDetected_doesNotInvalidateTokenMetadataCache() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .tokenSymbol("USDT")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setAddress("0xdac17f958d2ee523a2206206994597c13d831ec7");
        logObject.setTopics(Collections.singletonList(AdminEventMonitor.PAUSED_EVENT_TOPIC));
        logObject.setBlockNumber("0x64");
        logObject.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(logObject);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        adminEventMonitor.checkAdminEvents(90, 100);

        verify(tokenMetadataCache, never()).invalidate(anyString());
    }

    // --- 6.1 Upgraded event invalidates TokenMetadataCache ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_upgradedEventDetected_invalidatesTokenMetadataCache() throws Exception {
        TokenConfig token = TokenConfig.builder()
                .contractAddress("0xdac17f958d2ee523a2206206994597c13d831ec7")
                .tokenSymbol("USDT")
                .enabled(1)
                .build();
        doReturn(Collections.singletonList(token))
                .when(tokenConfigMapper).selectList(any(LambdaQueryWrapper.class));

        String newImplTopic = "0x000000000000000000000000abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setAddress("0xdac17f958d2ee523a2206206994597c13d831ec7");
        logObject.setTopics(Arrays.asList(AdminEventMonitor.UPGRADED_EVENT_TOPIC, newImplTopic));
        logObject.setBlockNumber("0xc8");
        logObject.setData("0x");

        EthLog ethLog = new EthLog();
        List<EthLog.LogResult> results = new ArrayList<EthLog.LogResult>();
        results.add(logObject);
        ethLog.setResult(results);

        Request mockRequest = mock(Request.class);
        doReturn(mockRequest).when(web3j).ethGetLogs(any(EthFilter.class));
        doReturn(ethLog).when(mockRequest).send();

        adminEventMonitor.checkAdminEvents(180, 200);

        verify(tokenMetadataCache).invalidate("0xdac17f958d2ee523a2206206994597c13d831ec7");
    }
}
