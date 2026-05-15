package com.erc20.platform.admin.vo.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenConfigUpdateRequest {

    private Integer depositConfirmBlocks;
    private Long minDepositAmount;
    private Long minWithdrawAmount;
    private Long withdrawFeeAmount;
    private Long collectionThreshold;
    private Integer enabled;
}
