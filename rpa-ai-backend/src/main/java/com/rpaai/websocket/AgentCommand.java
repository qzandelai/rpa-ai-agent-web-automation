package com.rpaai.websocket;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentCommand {
    private String taskId;
    private String stepId;
    private String action;
    private String target;
    private String value;
    private Integer timeout;
    private Boolean waitForNavigation;
}