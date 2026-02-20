package com.rpaai.service;

import com.rpaai.entity.AutomationTask;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScheduledTask {
    private String executionId;
    private AutomationTask task;
    private String userId;
    private int priority;
    private Long submitTime;
    private String status;  // PENDING, ASSIGNED, RUNNING
    private String assignedBrowser;
}