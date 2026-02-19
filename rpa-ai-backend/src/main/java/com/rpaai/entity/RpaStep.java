package com.rpaai.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RpaStep {
    // 步骤ID
    private Integer stepId;

    // 操作类型: open_url, click, input, wait, scroll, extract, submit等
    private String action;

    // 操作目标（CSS选择器/XPATH/URL）
    private String target;

    // 输入值（用于input操作）
    private String value;

    // 等待时间（秒，用于wait操作）
    private Integer waitTime;

    // 元素描述（用于AI理解）
    private String description;

    // 备用定位方式（当主定位失败时使用）
    private String fallbackTarget;

    // 是否必须执行
    private Boolean required = true;

    // 错误重试次数
    private Integer retryCount = 3;
}