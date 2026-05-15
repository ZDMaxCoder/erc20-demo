package com.erc20.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erc20.platform.admin.vo.request.AlertHandleRequest;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.dal.mapper.AlertRecordMapper;
import com.erc20.platform.dal.mapper.BlockSyncProgressMapper;
import com.erc20.platform.domain.entity.AlertRecord;
import com.erc20.platform.domain.entity.BlockSyncProgress;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@Api(tags = "Admin - Monitoring")
@RestController
@RequestMapping("/api/admin/v1")
public class AdminMonitorController {

    private final BlockSyncProgressMapper blockSyncProgressMapper;
    private final AlertRecordMapper alertRecordMapper;

    public AdminMonitorController(BlockSyncProgressMapper blockSyncProgressMapper,
                                  AlertRecordMapper alertRecordMapper) {
        this.blockSyncProgressMapper = blockSyncProgressMapper;
        this.alertRecordMapper = alertRecordMapper;
    }

    @ApiOperation("Get block sync status")
    @GetMapping("/block/sync-status")
    public Result<List<BlockSyncProgress>> getSyncStatus() {
        List<BlockSyncProgress> list = blockSyncProgressMapper.selectList(null);
        return Result.success(list);
    }

    @ApiOperation("Get unresolved alerts")
    @GetMapping("/alerts")
    public Result<List<AlertRecord>> getAlerts() {
        List<AlertRecord> alerts = alertRecordMapper.selectList(
                new LambdaQueryWrapper<AlertRecord>()
                        .eq(AlertRecord::getResolved, 0)
                        .orderByDesc(AlertRecord::getCreatedAt));
        return Result.success(alerts);
    }

    @ApiOperation("Handle alert")
    @PostMapping("/alerts/{id}/handle")
    public Result<Void> handleAlert(@PathVariable Long id,
                                    @RequestBody(required = false) AlertHandleRequest request) {
        AlertRecord alert = alertRecordMapper.selectById(id);
        if (alert == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        alert.setResolved(1);
        alert.setUpdatedAt(new Date());
        alertRecordMapper.updateById(alert);
        return Result.success();
    }
}
