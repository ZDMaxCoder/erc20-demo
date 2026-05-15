package com.erc20.platform.api.vo.response;

import com.erc20.platform.domain.entity.AccountBalance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceVO {

    private Long tokenId;
    private Long availableBalance;
    private Long frozenBalance;
    private Integer amountExponent;

    public static BalanceVO fromEntity(AccountBalance balance) {
        return BalanceVO.builder()
                .tokenId(balance.getTokenId())
                .availableBalance(balance.getAvailableBalance())
                .frozenBalance(balance.getFrozenBalance())
                .amountExponent(balance.getAmountExponent())
                .build();
    }
}
