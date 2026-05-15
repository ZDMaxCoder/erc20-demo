package com.erc20.platform.api.vo.response;

import com.erc20.platform.domain.entity.WithdrawRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRecordVO {

    private Long id;
    private String requestId;
    private Long tokenId;
    private String toAddress;
    private Long amount;
    private Integer amountExponent;
    private Long feeAmount;
    private String status;
    private String txHash;
    private Date createdAt;

    public static WithdrawRecordVO fromEntity(WithdrawRecord record) {
        return WithdrawRecordVO.builder()
                .id(record.getId())
                .requestId(record.getRequestId())
                .tokenId(record.getTokenId())
                .toAddress(record.getToAddress())
                .amount(record.getAmount())
                .amountExponent(record.getAmountExponent())
                .feeAmount(record.getFeeAmount())
                .status(record.getStatus())
                .txHash(record.getTxHash())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
