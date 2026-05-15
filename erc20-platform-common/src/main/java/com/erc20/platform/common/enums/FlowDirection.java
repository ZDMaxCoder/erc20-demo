package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FlowDirection {
    IN("IN", "Balance increase"),
    OUT("OUT", "Balance decrease");

    private final String code;
    private final String description;
}
