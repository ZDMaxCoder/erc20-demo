package com.erc20.platform.api.vo.response;

import com.erc20.platform.domain.entity.DepositRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRecordVO {

    private Long id;
    private String txHash;
    private Long tokenId;
    private String fromAddress;
    private String toAddress;
    private Long amount;
    private Integer amountExponent;
    private String status;
    private Long blockNumber;
    private Integer confirmations;
    private Date createdAt;

    public static DepositRecordVO fromEntity(DepositRecord record) {
        return DepositRecordVO.builder()
                .id(record.getId())
                .txHash(record.getTxHash())
                .tokenId(record.getTokenId())
                .fromAddress(record.getFromAddress())
                .toAddress(record.getToAddress())
                .amount(record.getAmount())
                .amountExponent(record.getAmountExponent())
                .status(record.getStatus())
                .blockNumber(record.getBlockNumber())
                .confirmations(record.getConfirmations())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
