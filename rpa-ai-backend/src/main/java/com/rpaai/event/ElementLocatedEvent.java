package com.rpaai.event;

import lombok.Getter;
import java.util.Map;

@Getter
public class ElementLocatedEvent extends RpaEvent {
    private final boolean found;
    private final Map<String, Object> data;

    public ElementLocatedEvent(Object source, String taskId, String stepId,
                               boolean found, Map<String, Object> data) {
        super(source, taskId, stepId);
        this.found = found;
        this.data = data;
    }
}