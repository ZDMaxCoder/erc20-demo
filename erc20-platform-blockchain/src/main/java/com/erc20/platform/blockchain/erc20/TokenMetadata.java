package com.erc20.platform.blockchain.erc20;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenMetadata {

    private String name;
    private String symbol;
    private Integer decimals;
}
