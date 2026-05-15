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
@TableName("t_block_sync_progress")
public class BlockSyncProgress {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer chainId;
    private Long lastSyncedBlock;
    private String lastSyncedBlockHash;
    private String status;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;
}
