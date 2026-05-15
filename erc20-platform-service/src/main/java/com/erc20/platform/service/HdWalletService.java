package com.erc20.platform.service;

import com.erc20.platform.service.dto.GeneratedAddress;

public interface HdWalletService {

    GeneratedAddress derive(int index);
}
