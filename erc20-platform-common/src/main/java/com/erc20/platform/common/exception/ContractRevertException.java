package com.erc20.platform.common.exception;

import com.erc20.platform.common.result.ErrorCode;

public class ContractRevertException extends BizException {

    public ContractRevertException(String detail) {
        super(ErrorCode.CHAIN_ERROR, detail);
    }
}
