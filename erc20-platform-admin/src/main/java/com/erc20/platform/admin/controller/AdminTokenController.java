package com.erc20.platform.admin.controller;

import com.erc20.platform.admin.vo.request.TokenAddRequest;
import com.erc20.platform.admin.vo.request.TokenConfigUpdateRequest;
import com.erc20.platform.common.exception.BizException;
import com.erc20.platform.common.result.ErrorCode;
import com.erc20.platform.common.result.Result;
import com.erc20.platform.common.util.AddressUtil;
import com.erc20.platform.dal.mapper.TokenConfigMapper;
import com.erc20.platform.domain.entity.TokenConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Date;

@Api(tags = "Admin - Token Config")
@RestController
@RequestMapping("/api/admin/v1/token")
public class AdminTokenController {

    private final TokenConfigMapper tokenConfigMapper;

    public AdminTokenController(TokenConfigMapper tokenConfigMapper) {
        this.tokenConfigMapper = tokenConfigMapper;
    }

    @ApiOperation("Add token")
    @PostMapping("/add")
    public Result<TokenConfig> addToken(@Valid @RequestBody TokenAddRequest request) {
        TokenConfig token = TokenConfig.builder()
                .tokenName(request.getTokenName())
                .tokenSymbol(request.getTokenSymbol())
                .contractAddress(AddressUtil.normalize(request.getContractAddress()))
                .decimals(request.getDecimals())
                .amountExponent(request.getDecimals())
                .chainId(request.getChainId())
                .depositConfirmBlocks(request.getDepositConfirmBlocks())
                .minDepositAmount(request.getMinDepositAmount())
                .minWithdrawAmount(request.getMinWithdrawAmount())
                .withdrawFeeAmount(request.getWithdrawFeeAmount())
                .collectionThreshold(request.getCollectionThreshold())
                .enabled(1)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
        tokenConfigMapper.insert(token);
        return Result.success(token);
    }

    @ApiOperation("Update token config")
    @PutMapping("/{id}/config")
    public Result<Void> updateConfig(@PathVariable Long id,
                                     @RequestBody TokenConfigUpdateRequest request) {
        TokenConfig token = tokenConfigMapper.selectById(id);
        if (token == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (request.getDepositConfirmBlocks() != null) {
            token.setDepositConfirmBlocks(request.getDepositConfirmBlocks());
        }
        if (request.getMinDepositAmount() != null) {
            token.setMinDepositAmount(request.getMinDepositAmount());
        }
        if (request.getMinWithdrawAmount() != null) {
            token.setMinWithdrawAmount(request.getMinWithdrawAmount());
        }
        if (request.getWithdrawFeeAmount() != null) {
            token.setWithdrawFeeAmount(request.getWithdrawFeeAmount());
        }
        if (request.getCollectionThreshold() != null) {
            token.setCollectionThreshold(request.getCollectionThreshold());
        }
        if (request.getEnabled() != null) {
            token.setEnabled(request.getEnabled());
        }
        token.setUpdatedAt(new Date());
        tokenConfigMapper.updateById(token);
        return Result.success();
    }
}
