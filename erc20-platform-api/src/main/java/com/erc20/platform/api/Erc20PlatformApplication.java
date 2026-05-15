package com.erc20.platform.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.erc20.platform")
public class Erc20PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(Erc20PlatformApplication.class, args);
    }
}
