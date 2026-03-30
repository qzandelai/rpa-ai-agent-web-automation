package com.rpaai.event;

import lombok.Getter;
import java.util.Map;

@Getter
public class StepCompletedEvent extends RpaEvent {
    private final boolean success;
    private final Map<String, Object> data;

    public StepCompletedEvent(Object source, String taskId, String stepId,
                              boolean success, Map<String, Object> data) {
        super(source, taskId, stepId);
        this.success = success;
        this.data = data;
    }
}