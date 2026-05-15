package com.erc20.platform.admin.vo.request;

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
public class TokenAddRequest {

    @NotBlank(message = "tokenName is required")
    private String tokenName;

    @NotBlank(message = "tokenSymbol is required")
    private String tokenSymbol;

    @NotBlank(message = "contractAddress is required")
    @EthAddress(message = "Invalid contract address")
    private String contractAddress;

    @NotNull(message = "decimals is required")
    private Integer decimals;

    @NotNull(message = "chainId is required")
    private Integer chainId;

    @NotNull(message = "depositConfirmBlocks is required")
    @Positive(message = "depositConfirmBlocks must be positive")
    private Integer depositConfirmBlocks;

    @NotNull(message = "minDepositAmount is required")
    private Long minDepositAmount;

    @NotNull(message = "minWithdrawAmount is required")
    private Long minWithdrawAmount;

    @NotNull(message = "withdrawFeeAmount is required")
    private Long withdrawFeeAmount;

    @NotNull(message = "collectionThreshold is required")
    private Long collectionThreshold;
}
