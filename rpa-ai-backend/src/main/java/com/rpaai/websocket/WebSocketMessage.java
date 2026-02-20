package com.rpaai.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type;
    private String taskId;
    private String stepId;
    private Map<String, Object> data;
    private Long timestamp = System.currentTimeMillis();
}