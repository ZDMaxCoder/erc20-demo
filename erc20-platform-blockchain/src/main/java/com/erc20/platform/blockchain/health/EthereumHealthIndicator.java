package com.erc20.platform.blockchain.health;

import com.erc20.platform.blockchain.config.Web3jProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.EthBlockNumber;

import java.math.BigInteger;

@Slf4j
@Component
public class EthereumHealthIndicator extends AbstractHealthIndicator {

    private final Web3jProvider web3jProvider;

    public EthereumHealthIndicator(Web3jProvider web3jProvider) {
        this.web3jProvider = web3jProvider;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            EthBlockNumber response = web3jProvider.sendWithFailover(
                    web3j -> web3j.ethBlockNumber().send());
            BigInteger blockNumber = response.getBlockNumber();
            builder.up()
                    .withDetail("blockNumber", blockNumber);
        } catch (Exception e) {
            log.warn("Ethereum RPC health check failed: {}", e.getMessage());
            builder.down()
                    .withDetail("error", e.getMessage());
        }
    }
}
