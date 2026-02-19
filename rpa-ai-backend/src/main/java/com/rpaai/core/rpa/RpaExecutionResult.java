package com.rpaai.core.rpa;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RpaExecutionResult {
    private boolean success;
    private int totalSteps;
    private int completedSteps;
    private String errorMessage;
    private List<RpaStepResult> stepResults = new ArrayList<>();
    private byte[] finalScreenshot;
}