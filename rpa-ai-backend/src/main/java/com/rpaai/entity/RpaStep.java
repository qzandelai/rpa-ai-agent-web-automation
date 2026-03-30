package com.rpaai.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RpaStep {
    private Integer stepId;
    private String action;
    private String target;
    private String value;
    private Integer waitTime;
    private String description;
    private String fallbackTarget;
    private Boolean required = true;
    private Integer retryCount = 3;
    private String imageTemplate;
    private Double imageThreshold = 0.8;
}