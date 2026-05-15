package com.erc20.platform.api.controller;

import com.erc20.platform.api.config.GlobalExceptionHandler;
import com.erc20.platform.api.filter.UserAuthFilter;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.service.AccountFlowService;
import com.erc20.platform.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AccountController.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {GlobalExceptionHandler.class, UserAuthFilter.class}
        )
)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private AccountFlowService accountFlowService;

    @Test
    void getBalance_validRequest_returns200WithBalanceVO() throws Exception {
        AccountBalance balance = AccountBalance.builder()
                .id(1L)
                .userId("user1")
                .tokenId(1L)
                .availableBalance(10000L)
                .frozenBalance(2000L)
                .amountExponent(6)
                .build();

        when(accountService.getBalance("user1", 1L)).thenReturn(balance);

        mockMvc.perform(get("/api/v1/account/balance")
                        .header("X-User-Id", "user1")
                        .param("tokenId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.availableBalance").value(10000))
                .andExpect(jsonPath("$.data.frozenBalance").value(2000));
    }
}
