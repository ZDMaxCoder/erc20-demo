package com.erc20.platform.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erc20.platform.admin.vo.request.RejectRequest;
import com.erc20.platform.common.enums.WithdrawStatus;
import com.erc20.platform.common.result.PageResult;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.WithdrawService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "Admin - Withdraw Review")
@RestController
@RequestMapping("/api/admin/v1/withdraw")
public class AdminWithdrawController {

    private final WithdrawService withdrawService;
    private final WithdrawRecordMapper withdrawRecordMapper;

    public AdminWithdrawController(WithdrawService withdrawService,
                                   WithdrawRecordMapper withdrawRecordMapper) {
        this.withdrawService = withdrawService;
        this.withdrawRecordMapper = withdrawRecordMapper;
    }

    @ApiOperation("Get pending review withdrawals")
    @GetMapping("/pending-review")
    public Result<PageResult<WithdrawRecord>> getPendingReview(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<WithdrawRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<WithdrawRecord>()
                .eq(WithdrawRecord::getStatus, WithdrawStatus.PENDING_REVIEW.getCode())
                .orderByDesc(WithdrawRecord::getCreatedAt);
        IPage<WithdrawRecord> result = withdrawRecordMapper.selectPage(pageParam, wrapper);

        PageResult<WithdrawRecord> pageResult = PageResult.<WithdrawRecord>builder()
                .records(result.getRecords())
                .total(result.getTotal())
                .current(result.getCurrent())
                .size(result.getSize())
                .pages(result.getPages())
                .build();
        return Result.success(pageResult);
    }

    @ApiOperation("Approve withdrawal")
    @PostMapping("/{id}/approve")
    public Result<Void> approve(
            @PathVariable Long id,
            @RequestAttribute("operator") String operator) {
        withdrawService.approve(id, operator);
        return Result.success();
    }

    @ApiOperation("Reject withdrawal")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(
            @PathVariable Long id,
            @RequestAttribute("operator") String operator,
            @Valid @RequestBody RejectRequest request) {
        withdrawService.reject(id, operator, request.getReason());
        return Result.success();
    }
}
