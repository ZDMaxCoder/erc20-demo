package com.erc20.platform.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AlertLevel {
    INFO("INFO", "Informational"),
    WARN("WARN", "Warning"),
    CRITICAL("CRITICAL", "Critical alert");

    private final String code;
    private final String description;
}
