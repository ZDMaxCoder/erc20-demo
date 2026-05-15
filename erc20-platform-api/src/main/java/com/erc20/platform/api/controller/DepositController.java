package com.erc20.platform.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erc20.platform.api.vo.request.DepositAddressRequest;
import com.erc20.platform.api.vo.response.DepositAddressVO;
import com.erc20.platform.api.vo.response.DepositRecordVO;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.common.result.PageResult;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.dal.mapper.DepositRecordMapper;
import com.erc20.platform.domain.entity.DepositRecord;
import com.erc20.platform.service.AddressService;
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

@Api(tags = "Deposit")
@RestController
@RequestMapping("/api/v1/deposit")
public class DepositController {

    private final AddressService addressService;
    private final DepositRecordMapper depositRecordMapper;

    public DepositController(AddressService addressService,
                             DepositRecordMapper depositRecordMapper) {
        this.addressService = addressService;
        this.depositRecordMapper = depositRecordMapper;
    }

    @ApiOperation("Allocate deposit address")
    @PostMapping("/address")
    public Result<DepositAddressVO> allocateAddress(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody DepositAddressRequest request) {
        String address = addressService.allocateDepositAddress(userId, request.getTokenId());
        return Result.success(DepositAddressVO.builder().address(address).build());
    }

    @ApiOperation("Query deposit records")
    @GetMapping("/records")
    public Result<PageResult<DepositRecordVO>> getRecords(
            @RequestAttribute("userId") String userId,
            @RequestParam Long tokenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DepositRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DepositRecord> wrapper = new LambdaQueryWrapper<DepositRecord>()
                .eq(DepositRecord::getUserId, userId)
                .eq(DepositRecord::getTokenId, tokenId)
                .orderByDesc(DepositRecord::getCreatedAt);
        IPage<DepositRecord> result = depositRecordMapper.selectPage(pageParam, wrapper);

        List<DepositRecordVO> voList = new ArrayList<>();
        for (DepositRecord record : result.getRecords()) {
            voList.add(DepositRecordVO.fromEntity(record));
        }

        PageResult<DepositRecordVO> pageResult = PageResult.<DepositRecordVO>builder()
                .records(voList)
                .total(result.getTotal())
                .current(result.getCurrent())
                .size(result.getSize())
                .pages(result.getPages())
                .build();
        return Result.success(pageResult);
    }

    @ApiOperation("Get deposit detail")
    @GetMapping("/{id}")
    public Result<DepositRecordVO> getDetail(
            @RequestAttribute("userId") String userId,
            @PathVariable Long id) {
        DepositRecord record = depositRecordMapper.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.success(DepositRecordVO.fromEntity(record));
    }
}
