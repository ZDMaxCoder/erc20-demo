package com.erc20.platform.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erc20.platform.api.vo.request.WithdrawCreateRequest;
import com.erc20.platform.api.vo.response.WithdrawCreateVO;
import com.erc20.platform.api.vo.response.WithdrawRecordVO;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.common.result.PageResult;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.dal.mapper.WithdrawRecordMapper;
import com.erc20.platform.domain.entity.WithdrawRecord;
import com.erc20.platform.service.WithdrawService;
import com.erc20.platform.service.dto.WithdrawRequest;
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
import java.util.ArrayList;
import java.util.List;

@Api(tags = "Withdraw")
@RestController
@RequestMapping("/api/v1/withdraw")
public class WithdrawController {

    private final WithdrawService withdrawService;
    private final WithdrawRecordMapper withdrawRecordMapper;

    public WithdrawController(WithdrawService withdrawService,
                              WithdrawRecordMapper withdrawRecordMapper) {
        this.withdrawService = withdrawService;
        this.withdrawRecordMapper = withdrawRecordMapper;
    }

    @ApiOperation("Create withdrawal")
    @PostMapping("/create")
    public Result<WithdrawCreateVO> create(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody WithdrawCreateRequest request) {
        WithdrawRequest serviceRequest = WithdrawRequest.builder()
                .requestId(request.getRequestId())
                .userId(userId)
                .tokenId(request.getTokenId())
                .toAddress(request.getToAddress())
                .amount(request.getAmount())
                .build();

        WithdrawRecord record = withdrawService.createWithdraw(serviceRequest);
        return Result.success(WithdrawCreateVO.builder().withdrawId(record.getId()).build());
    }

    @ApiOperation("Query withdrawal records")
    @GetMapping("/records")
    public Result<PageResult<WithdrawRecordVO>> getRecords(
            @RequestAttribute("userId") String userId,
            @RequestParam Long tokenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<WithdrawRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<WithdrawRecord>()
                .eq(WithdrawRecord::getUserId, userId)
                .eq(WithdrawRecord::getTokenId, tokenId)
                .orderByDesc(WithdrawRecord::getCreatedAt);
        IPage<WithdrawRecord> result = withdrawRecordMapper.selectPage(pageParam, wrapper);

        List<WithdrawRecordVO> voList = new ArrayList<>();
        for (WithdrawRecord record : result.getRecords()) {
            voList.add(WithdrawRecordVO.fromEntity(record));
        }

        PageResult<WithdrawRecordVO> pageResult = PageResult.<WithdrawRecordVO>builder()
                .records(voList)
                .total(result.getTotal())
                .current(result.getCurrent())
                .size(result.getSize())
                .pages(result.getPages())
                .build();
        return Result.success(pageResult);
    }

    @ApiOperation("Get withdrawal detail")
    @GetMapping("/{id}")
    public Result<WithdrawRecordVO> getDetail(
            @RequestAttribute("userId") String userId,
            @PathVariable Long id) {
        WithdrawRecord record = withdrawRecordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.success(WithdrawRecordVO.fromEntity(record));
    }
}
