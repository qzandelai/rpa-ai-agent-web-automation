package com.rpaai.core.rpa;

import lombok.Data;

@Data
public class RpaStepResult {
    private Integer stepId;
    private boolean success;
    private String message;
    private String errorMessage;
    private long executionTimeMs;

    public static RpaStepResult success(Integer stepId, String message) {
        RpaStepResult result = new RpaStepResult();
        result.setStepId(stepId);
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public static RpaStepResult fail(Integer stepId, String errorMessage) {
        RpaStepResult result = new RpaStepResult();
        result.setStepId(stepId);
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}