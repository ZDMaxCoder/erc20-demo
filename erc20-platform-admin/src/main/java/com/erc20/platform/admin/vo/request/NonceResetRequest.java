package com.erc20.platform.admin.vo.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonceResetRequest {

    @NotNull(message = "chainId is required")
    private Integer chainId;

    @NotBlank(message = "walletAddress is required")
    private String walletAddress;
}
