package com.erc20.platform.api.vo.response;

import com.erc20.platform.domain.entity.AccountFlow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountFlowVO {

    private Long id;
    private Long tokenId;
    private String flowType;
    private String flowDirection;
    private Long amount;
    private Integer amountExponent;
    private Long balanceBefore;
    private Long balanceAfter;
    private Date createdAt;

    public static AccountFlowVO fromEntity(AccountFlow flow) {
        return AccountFlowVO.builder()
                .id(flow.getId())
                .tokenId(flow.getTokenId())
                .flowType(flow.getFlowType())
                .flowDirection(flow.getFlowDirection())
                .amount(flow.getAmount())
                .amountExponent(flow.getAmountExponent())
                .balanceBefore(flow.getBalanceBefore())
                .balanceAfter(flow.getBalanceAfter())
                .createdAt(flow.getCreatedAt())
                .build();
    }
}
