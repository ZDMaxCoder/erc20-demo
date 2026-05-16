package com.erc20.platform.common.exception;

import com.erc20.platform.common.result.ErrorCode;

public class AmountOverflowException extends BizException {

    public AmountOverflowException(String detail) {
        super(ErrorCode.AMOUNT_OVERFLOW, detail);
    }
}
