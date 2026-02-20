package com.rpaai.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BrowserSession {
    private String websocketSessionId;
    private String userId;
    private String browserFingerprint;
    private String currentUrl;
    private String currentTitle;
    private Long connectedTime;
    private Long lastHeartbeat;
    private Long lastActivityTime;
    private String status;
    private String assignedTaskId;
}