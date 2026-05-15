package com.erc20.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_transaction_record")
public class TransactionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String txHash;
    private Integer chainId;
    private String fromAddress;
    private String toAddress;
    private String txType;
    private Long tokenId;
    private Long amount;
    private Integer amountExponent;
    private Long gasPrice;
    private Long gasLimit;
    private Long gasUsed;
    private Long nonce;
    private String status;
    private String bizType;
    private Long bizId;
    private Long blockNumber;
    private String blockHash;
    private String rawTx;
    private String replacedByTxHash;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;
}
