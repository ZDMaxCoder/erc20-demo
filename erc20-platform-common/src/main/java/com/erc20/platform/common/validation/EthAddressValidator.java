package com.erc20.platform.common.validation;

import com.erc20.platform.common.util.AddressUtil;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class EthAddressValidator implements ConstraintValidator<EthAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return AddressUtil.isValid(value);
    }
}
