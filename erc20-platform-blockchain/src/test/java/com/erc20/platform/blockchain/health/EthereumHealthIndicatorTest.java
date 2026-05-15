package com.erc20.platform.blockchain.health;

import com.erc20.platform.blockchain.config.Web3jProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

import java.io.IOException;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EthereumHealthIndicatorTest {

    @Mock
    private Web3jProvider web3jProvider;

    private EthereumHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new EthereumHealthIndicator(web3jProvider);
    }

    @Test
    void rpcResponds_statusUp_withBlockNumber() throws Exception {
        EthBlockNumber response = new EthBlockNumber();
        response.setResult("0x1A4"); // 420

        doReturn(response).when(web3jProvider).sendWithFailover(any());

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(BigInteger.valueOf(420), health.getDetails().get("blockNumber"));
    }

    @Test
    void rpcTimeout_statusDown() throws Exception {
        doThrow(new IOException("Connection timed out"))
                .when(web3jProvider).sendWithFailover(any());

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
    }
}
