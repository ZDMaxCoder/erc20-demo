package com.erc20.platform.service.dto;

import com.erc20.platform.common.enums.FlowType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperateRequest {

    private String userId;
    private Long tokenId;
    private Long amount;
    private Integer amountExponent;
    private FlowType flowType;
    private Long bizId;
    private String idempotentKey;
}
