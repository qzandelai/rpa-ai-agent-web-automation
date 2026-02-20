package com.rpaai.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StepResult {
    private Integer stepId;
    private boolean success;
    private String message;
    private String error;
    private Long executionTimeMs;

    public static StepResult success(Integer stepId, String message) {
        return StepResult.builder()
                .stepId(stepId)
                .success(true)
                .message(message)
                .build();
    }

    public static StepResult fail(Integer stepId, String error) {
        return StepResult.builder()
                .stepId(stepId)
                .success(false)
                .error(error)
                .build();
    }
}