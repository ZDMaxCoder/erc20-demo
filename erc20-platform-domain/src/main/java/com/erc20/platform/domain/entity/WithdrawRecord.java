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
@TableName("t_withdraw_record")
public class WithdrawRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String idempotentKey;
    private String userId;
    private Long tokenId;
    private String toAddress;
    private Long amount;
    private Integer amountExponent;
    private Long feeAmount;
    private String status;
    private String txHash;
    private String errorMessage;
    private Integer retryCount;
    private Date createdAt;
    private Date updatedAt;
}
