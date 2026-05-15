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
@TableName("t_deposit_record")
public class DepositRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String txHash;
    private Integer logIndex;
    private String idempotentKey;
    private String userId;
    private Long tokenId;
    private String fromAddress;
    private String toAddress;
    private Long amount;
    private Integer amountExponent;
    private String status;
    private Long blockNumber;
    private String blockHash;
    private Integer confirmations;
    private Integer requiredConfirmations;
    private Integer credited;
    private Date createdAt;
    private Date updatedAt;
}
