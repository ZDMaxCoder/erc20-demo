package com.erc20.platform.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erc20.platform.api.vo.response.AccountFlowVO;
import com.erc20.platform.api.vo.response.BalanceVO;
import com.erc20.platform.common.result.PageResult;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.domain.entity.AccountBalance;
import com.erc20.platform.domain.entity.AccountFlow;
import com.erc20.platform.service.AccountFlowService;
import com.erc20.platform.service.AccountService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Api(tags = "Account")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;
    private final AccountFlowService accountFlowService;

    public AccountController(AccountService accountService,
                             AccountFlowService accountFlowService) {
        this.accountService = accountService;
        this.accountFlowService = accountFlowService;
    }

    @ApiOperation("Get account balance")
    @GetMapping("/balance")
    public Result<BalanceVO> getBalance(
            @RequestAttribute("userId") String userId,
            @RequestParam Long tokenId) {
        AccountBalance balance = accountService.getBalance(userId, tokenId);
        return Result.success(BalanceVO.fromEntity(balance));
    }

    @ApiOperation("Query account flows")
    @GetMapping("/flows")
    public Result<PageResult<AccountFlowVO>> getFlows(
            @RequestAttribute("userId") String userId,
            @RequestParam Long tokenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<AccountFlow> result = accountFlowService.queryFlows(userId, tokenId, page, size);

        List<AccountFlowVO> voList = new ArrayList<>();
        for (AccountFlow flow : result.getRecords()) {
            voList.add(AccountFlowVO.fromEntity(flow));
        }

        PageResult<AccountFlowVO> pageResult = PageResult.<AccountFlowVO>builder()
                .records(voList)
                .total(result.getTotal())
                .current(result.getCurrent())
                .size(result.getSize())
                .pages(result.getPages())
                .build();
        return Result.success(pageResult);
    }
}
