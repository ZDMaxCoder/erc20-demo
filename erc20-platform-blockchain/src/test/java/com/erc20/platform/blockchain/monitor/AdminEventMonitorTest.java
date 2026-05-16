package com.erc20.platform.blockchain.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventMonitorTest {

    @Mock private Web3j web3j;
    @Mock private TokenConfigMapper tokenConfigMapper;
    @Mock private AlertService alertService;

    private AdminEventMonitor adminEventMonitor;

    @BeforeEach
    void setUp() {
        adminEventMonitor = new AdminEventMonitor(web3j, tokenConfigMapper, alertService);
    }

    // --- 13.1 Paused() event detected → CRITICAL alert ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_pausedEventDetected_raisesCriticalAlert() throws Exception {
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

        verify(alertService).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                eq("Token USDT paused at block 100"));
    }

    // --- 13.2 Upgraded(address) event detected → CRITICAL alert with new impl address ---

    @SuppressWarnings("unchecked")
    @Test
    void checkAdminEvents_upgradedEventDetected_raisesCriticalAlertWithImplAddress() throws Exception {
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

        verify(alertService).alert(
                eq("TOKEN_ADMIN_EVENT"),
                eq(AlertLevel.CRITICAL),
                eq("Token USDT upgraded to 0xabcdefabcdefabcdefabcdefabcdefabcdefabcd at block 200"));
    }
}
