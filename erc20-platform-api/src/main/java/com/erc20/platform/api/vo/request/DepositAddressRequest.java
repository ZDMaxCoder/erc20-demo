package com.erc20.platform.api.vo.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositAddressRequest {

    @NotNull(message = "tokenId is required")
    private Long tokenId;
}
