package com.erc20.platform.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.erc20.platform")
public class Erc20AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(Erc20AdminApplication.class, args);
    }
}
