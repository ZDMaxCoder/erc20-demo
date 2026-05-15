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
@TableName("t_address_blacklist")
public class AddressBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String address;
    private String reason;
    private String operator;
    private Date createdAt;
    private Date updatedAt;
}
