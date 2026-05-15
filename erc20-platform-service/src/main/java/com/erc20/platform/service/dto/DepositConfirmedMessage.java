package com.erc20.platform.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositConfirmedMessage {

    private Long depositId;
    private String userId;
    private Long tokenId;
    private String address;
    private Long amount;
    private Integer amountExponent;
}
