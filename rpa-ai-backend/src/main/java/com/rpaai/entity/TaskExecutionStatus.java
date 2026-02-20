package com.rpaai.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskExecutionStatus {
    private String executionId;
    private String status;        // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    private int currentStep;
    private int totalSteps;
    private String currentUrl;
    private String errorMessage;
    private Long startTime;
    private Long durationMs;
}