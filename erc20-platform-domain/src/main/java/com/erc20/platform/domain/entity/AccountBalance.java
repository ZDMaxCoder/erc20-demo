package com.erc20.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_account_balance")
public class AccountBalance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long tokenId;
    private Long availableBalance;
    private Long frozenBalance;
    private Integer amountExponent;
    @Version
    private Long version;
    private Date createdAt;
    private Date updatedAt;
}
