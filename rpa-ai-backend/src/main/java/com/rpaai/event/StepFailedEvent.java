package com.rpaai.event;

import lombok.Getter;

import java.util.Map;

@Getter
public class StepFailedEvent extends RpaEvent {
    private final String error;
    private final Map<String, Object> context;

    public StepFailedEvent(Object source, String taskId, String stepId,
                           String error, Map<String, Object> context) {
        super(source, taskId, stepId);
        this.error = error;
        this.context = context;
    }
}