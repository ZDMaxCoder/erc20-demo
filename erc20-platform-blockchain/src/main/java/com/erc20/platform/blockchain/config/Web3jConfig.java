package com.erc20.platform.blockchain.config;

import com.erc20.platform.service.AlertService;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

@Configuration
public class Web3jConfig {

    @Value("${blockchain.rpc.primary-url}")
    private String primaryUrl;

    @Value("${blockchain.rpc.backup-url:}")
    private String backupUrl;

    @Bean
    public OkHttpClient web3jHttpClient() {
        return new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(10, 30, TimeUnit.SECONDS))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public Web3jProvider web3jProvider(OkHttpClient web3jHttpClient, AlertService alertService) {
        Web3j primary = Web3j.build(new HttpService(primaryUrl, web3jHttpClient));
        Web3j backup = (backupUrl != null && !backupUrl.isEmpty())
                ? Web3j.build(new HttpService(backupUrl, web3jHttpClient))
                : null;
        Web3jProvider provider = new Web3jProvider(primary, backup);
        provider.setAlertService(alertService);
        return provider;
    }
}
