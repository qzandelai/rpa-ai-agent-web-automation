package com.rpaai.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class RpaEvent extends ApplicationEvent {
    private final String taskId;
    private final String stepId;
    // ❌ 原代码：private final long timestamp; （与父类冲突）
    // ✅ 改为 eventTime 或其他名称
    private final long eventTime;

    public RpaEvent(Object source, String taskId, String stepId) {
        super(source);
        this.taskId = taskId;
        this.stepId = stepId;
        this.eventTime = System.currentTimeMillis();
    }
}