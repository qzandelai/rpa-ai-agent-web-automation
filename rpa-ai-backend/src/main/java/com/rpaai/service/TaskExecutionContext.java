package com.rpaai.service;

import com.rpaai.entity.AutomationTask;
import com.rpaai.entity.RpaStep;
import com.rpaai.entity.StepResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class TaskExecutionContext {
    private String executionId;
    private String browserSessionId;
    private AutomationTask task;
    private List<RpaStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;
    private List<StepResult> completedSteps = new ArrayList<>();
    private String status = "PENDING";
    private String errorMessage;
    private Long startTime;
    private String currentUrl;
    private volatile boolean cancelled = false;

    private Map<String, CompletableFuture<Map<String, Object>>> pendingSteps = new ConcurrentHashMap<>();

    public void registerPendingStep(Integer stepId, CompletableFuture<Map<String, Object>> future) {
        pendingSteps.put(String.valueOf(stepId), future);
    }

    public CompletableFuture<Map<String, Object>> getPendingStep(String stepId) {
        return pendingSteps.get(stepId);
    }

    public CompletableFuture<Map<String, Object>> removePendingStep(String stepId) {
        return pendingSteps.remove(stepId);
    }

    public void replaceRemainingSteps(List<RpaStep> newSteps) {
        int current = this.currentStepIndex;
        List<RpaStep> newList = new ArrayList<>();
        newList.addAll(this.steps.subList(0, current + 1));
        newList.addAll(newSteps);
        this.steps = newList;
    }
}