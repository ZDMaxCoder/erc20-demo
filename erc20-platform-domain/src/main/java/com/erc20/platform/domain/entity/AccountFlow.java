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
@TableName("t_account_flow")
public class AccountFlow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long tokenId;
    private String flowType;
    private String flowDirection;
    private Long amount;
    private Integer amountExponent;
    private Long balanceBefore;
    private Long balanceAfter;
    private Long bizId;
    private String idempotentKey;
    private String remark;
    private Date createdAt;
}
