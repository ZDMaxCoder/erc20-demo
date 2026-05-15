package com.erc20.platform.admin.controller;

import com.erc20.platform.admin.config.GlobalExceptionHandler;
import com.erc20.platform.admin.filter.AdminAuthFilter;
import com.erc20.platform.blockchain.nonce.NonceManager;
import com.erc20.platform.dal.mapper.WalletConfigMapper;
import com.erc20.platform.service.CollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminWalletController.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {GlobalExceptionHandler.class, AdminAuthFilter.class}
        )
)
class AdminWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NonceManager nonceManager;

    @MockBean
    private CollectionService collectionService;

    @MockBean
    private WalletConfigMapper walletConfigMapper;

    @Test
    void nonceReset_callsNonceManager() throws Exception {
        mockMvc.perform(post("/api/admin/v1/nonce/reset")
                        .header("X-Operator", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chainId\":1,\"walletAddress\":\"0x1234567890abcdef1234567890abcdef12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(nonceManager).resetNonce(1, "0x1234567890abcdef1234567890abcdef12345678");
    }
}
