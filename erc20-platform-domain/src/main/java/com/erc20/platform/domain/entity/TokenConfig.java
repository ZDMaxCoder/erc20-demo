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
@TableName("t_token_config")
public class TokenConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tokenName;
    private String tokenSymbol;
    private String contractAddress;
    private Integer decimals;
    private Integer amountExponent;
    private Integer chainId;
    private Integer depositConfirmBlocks;
    private Long minDepositAmount;
    private Long minWithdrawAmount;
    private Long withdrawFeeAmount;
    private Long collectionThreshold;
    private Integer enabled;
    private Date createdAt;
    private Date updatedAt;
}
