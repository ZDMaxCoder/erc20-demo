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
@TableName("t_alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertLevel;
    private String alertType;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Integer resolved;
    private Date createdAt;
    private Date updatedAt;
}
