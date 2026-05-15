package com.erc20.platform.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {

    private String alertType;
    private String alertLevel;
    private String content;
    private String source;
    private Long timestamp;
}
