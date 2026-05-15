package com.erc20.platform.api.controller;

import com.erc20.platform.api.config.GlobalExceptionHandler;
import com.erc20.platform.api.filter.UserAuthFilter;
import com.erc20.platform.common.result.PageResult;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.service.AddressService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = DepositController.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {GlobalExceptionHandler.class, UserAuthFilter.class}
        )
)
class DepositControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddressService addressService;

    @MockBean
    private DepositRecordMapper depositRecordMapper;

    @Test
    void postDepositAddress_validTokenId_returns200WithAddress() throws Exception {
        when(addressService.allocateDepositAddress("user1", 1L))
                .thenReturn("0x1234567890abcdef1234567890abcdef12345678");

        mockMvc.perform(post("/api/v1/deposit/address")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenId\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.address").value("0x1234567890abcdef1234567890abcdef12345678"));
    }

    @Test
    void getDepositRecords_withPagination_returns200WithPageResult() throws Exception {
        DepositRecord record = DepositRecord.builder()
                .id(1L)
                .txHash("0xabc")
                .userId("user1")
                .tokenId(1L)
                .amount(1000L)
                .amountExponent(6)
                .status("SUCCESS")
                .fromAddress("0x1111111111111111111111111111111111111111")
                .toAddress("0x2222222222222222222222222222222222222222")
                .blockNumber(100L)
                .confirmations(12)
                .createdAt(new Date())
                .build();

        Page<DepositRecord> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList(record));
        page.setTotal(1);

        when(depositRecordMapper.selectPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/deposit/records")
                        .header("X-User-Id", "user1")
                        .param("tokenId", "1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].txHash").value("0xabc"));
    }
}
