package com.erc20.platform.blockchain.gas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;

@Configuration
@EnableConfigurationProperties(GasProperties.class)
public class GasConfig {

    @Bean
    @ConditionalOnProperty(name = "blockchain.gas.strategy", havingValue = "eip1559", matchIfMissing = true)
    public GasStrategy eip1559GasStrategy(Web3j web3j, GasProperties gasProperties) {
        return new EIP1559GasStrategy(web3j, gasProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "blockchain.gas.strategy", havingValue = "legacy")
    public GasStrategy legacyGasStrategy(Web3j web3j, GasProperties gasProperties) {
        return new LegacyGasStrategy(web3j, gasProperties);
    }
}
