package com.erc20.platform.api.controller;

import com.erc20.platform.api.config.GlobalExceptionHandler;
import com.erc20.platform.api.filter.UserAuthFilter;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.WithdrawService;
import com.erc20.platform.service.dto.WithdrawRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = WithdrawController.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {GlobalExceptionHandler.class, UserAuthFilter.class}
        )
)
class WithdrawControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WithdrawService withdrawService;

    @MockBean
    private WithdrawRecordMapper withdrawRecordMapper;

    @Test
    void postWithdrawCreate_validRequest_returns200WithId() throws Exception {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(100L)
                .requestId("req-001")
                .userId("user1")
                .tokenId(1L)
                .toAddress("0x1234567890abcdef1234567890abcdef12345678")
                .amount(5000L)
                .status("PENDING_REVIEW")
                .createdAt(new Date())
                .build();

        when(withdrawService.createWithdraw(any(WithdrawRequest.class))).thenReturn(record);

        mockMvc.perform(post("/api/v1/withdraw/create")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenId\":1,\"toAddress\":\"0x1234567890abcdef1234567890abcdef12345678\",\"amount\":5000,\"requestId\":\"req-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.withdrawId").value(100));
    }

    @Test
    void postWithdrawCreate_invalidAddress_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/withdraw/create")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenId\":1,\"toAddress\":\"invalid-address\",\"amount\":5000,\"requestId\":\"req-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void postWithdrawCreate_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/withdraw/create")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenId\":1,\"toAddress\":\"0x1234567890abcdef1234567890abcdef12345678\",\"amount\":-100,\"requestId\":\"req-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }
}
