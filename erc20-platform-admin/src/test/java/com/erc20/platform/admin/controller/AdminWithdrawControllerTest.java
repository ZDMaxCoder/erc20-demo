package com.erc20.platform.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erc20.platform.admin.config.GlobalExceptionHandler;
import com.erc20.platform.admin.filter.AdminAuthFilter;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.WithdrawService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Date;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminWithdrawController.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {GlobalExceptionHandler.class, AdminAuthFilter.class}
        )
)
class AdminWithdrawControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WithdrawService withdrawService;

    @MockBean
    private WithdrawRecordMapper withdrawRecordMapper;

    @Test
    void getPendingReview_returnsPaginatedList() throws Exception {
        WithdrawRecord record = WithdrawRecord.builder()
                .id(1L)
                .requestId("req-001")
                .userId("user1")
                .tokenId(1L)
                .toAddress("0x1234567890abcdef1234567890abcdef12345678")
                .amount(5000L)
                .status("PENDING_REVIEW")
                .createdAt(new Date())
                .build();

        Page<WithdrawRecord> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(record));
        page.setTotal(1);

        when(withdrawRecordMapper.selectPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/v1/withdraw/pending-review")
                        .header("X-Operator", "admin1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING_REVIEW"));
    }

    @Test
    void approve_returns200() throws Exception {
        mockMvc.perform(post("/api/admin/v1/withdraw/1/approve")
                        .header("X-Operator", "admin1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(withdrawService).approve(eq(1L), eq("admin1"));
    }

    @Test
    void reject_withReason_returns200() throws Exception {
        mockMvc.perform(post("/api/admin/v1/withdraw/1/reject")
                        .header("X-Operator", "admin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Suspicious activity\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(withdrawService).reject(eq(1L), eq("admin1"), eq("Suspicious activity"));
    }
}
