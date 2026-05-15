package com.erc20.platform.api.vo.request;

import com.erc20.platform.common.validation.EthAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawCreateRequest {

    @NotNull(message = "tokenId is required")
    private Long tokenId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private Long amount;

    @NotBlank(message = "toAddress is required")
    @EthAddress(message = "Invalid Ethereum address")
    private String toAddress;

    @NotBlank(message = "requestId is required")
    private String requestId;
}
