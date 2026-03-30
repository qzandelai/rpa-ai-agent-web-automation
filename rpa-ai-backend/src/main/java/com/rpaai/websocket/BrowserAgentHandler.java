package com.rpaai.websocket;

import com.alibaba.fastjson2.JSON;
import com.rpaai.entity.BrowserSession;
import com.rpaai.event.*;
import com.rpaai.service.BrowserSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BrowserAgentHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> browserSessions = new ConcurrentHashMap<>();

    @Autowired
    private BrowserSessionManager sessionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        log.info("🔌 浏览器扩展已连接: {}", sessionId);
        browserSessions.put(sessionId, session);

        sendMessage(session, WebSocketMessage.builder()
                .type("CONNECTED")
                .data(Map.of("sessionId", sessionId))
                .build());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("📨 收到消息 [{}]: {}", session.getId(), payload);

        try {
            WebSocketMessage msg = JSON.parseObject(payload, WebSocketMessage.class);
            handleMessage(session, msg);
        } catch (Exception e) {
            log.error("❌ 消息处理失败", e);
            sendError(session, "消息格式错误: " + e.getMessage());
        }
    }

    private void handleMessage(WebSocketSession session, WebSocketMessage msg) {
        switch (msg.getType()) {
            case "REGISTER" -> handleRegister(session, msg);
            case "PAGE_INFO" -> handlePageInfo(session, msg);
            case "ELEMENT_FOUND" -> handleElementFound(session, msg);
            case "ACTION_RESULT" -> handleActionResult(session, msg);
            case "ERROR" -> handleBrowserError(session, msg);
            case "HEARTBEAT" -> handleHeartbeat(session, msg);
            default -> log.warn("⚠️ 未知消息类型: {}", msg.getType());
        }
    }

    private void handleRegister(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        String userId = (String) data.get("userId");
        String fingerprint = (String) data.get("fingerprint");

        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }

        BrowserSession browserSession = BrowserSession.builder()
                .websocketSessionId(session.getId())
                .userId(userId)
                .browserFingerprint(fingerprint)
                .connectedTime(System.currentTimeMillis())
                .status("ACTIVE")
                .build();

        sessionManager.registerSession(browserSession);

        sendMessage(session, WebSocketMessage.builder()
                .type("REGISTERED")
                .data(Map.of("status", "success"))
                .build());
    }

    private void handlePageInfo(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        String url = (String) data.get("url");
        String title = (String) data.get("title");

        sessionManager.updatePageInfo(session.getId(), url, title);

        eventPublisher.publishEvent(new PageChangedEvent(
                this, session.getId(), url, title));
    }

    private void handleElementFound(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        String taskId = (String) data.get("taskId");
        String stepId = (String) data.get("stepId");
        boolean found = data.get("found") != null && (boolean) data.get("found");

        eventPublisher.publishEvent(new ElementLocatedEvent(
                this, taskId, stepId, found, data));
    }

    private void handleActionResult(WebSocketSession session, WebSocketMessage msg) {
        String taskId = msg.getTaskId();
        String stepId = msg.getStepId();
        Map<String, Object> data = msg.getData();

        if (taskId == null) {
            log.error("❌ ACTION_RESULT缺少taskId: {}", msg);
            return;
        }
        if (stepId == null) {
            log.error("❌ ACTION_RESULT缺少stepId: {}", msg);
            return;
        }

        Boolean success = data != null ? (Boolean) data.get("success") : false;

        log.info("✅ 处理动作结果: taskId={}, stepId={}, success={}", taskId, stepId, success);

        eventPublisher.publishEvent(new StepCompletedEvent(
                this, taskId, stepId, success, data));
    }

    private void handleBrowserError(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        String taskId = data != null ? (String) data.get("taskId") : null;
        String error = data != null ? (String) data.get("error") : "未知错误";
        String stepId = data != null ? (String) data.get("stepId") : null;

        if (taskId != null) {
            eventPublisher.publishEvent(new StepFailedEvent(
                    this, taskId, stepId, error, data));
        }
    }

    private void handleHeartbeat(WebSocketSession session, WebSocketMessage msg) {
        sessionManager.updateHeartbeat(session.getId());
        sendMessage(session, WebSocketMessage.builder()
                .type("HEARTBEAT_ACK")
                .data(Map.of("timestamp", System.currentTimeMillis()))
                .build());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        browserSessions.remove(sessionId);
        sessionManager.unregisterSession(sessionId);
        log.info("🔌 浏览器扩展断开: {}", sessionId);
    }

    public void sendCommand(String browserSessionId, AgentCommand command) {
        WebSocketSession session = browserSessions.get(browserSessionId);

        if (session == null || !session.isOpen()) {
            throw new RuntimeException("浏览器会话不存在或已关闭: " + browserSessionId);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("action", command.getAction());
        data.put("target", command.getTarget());
        data.put("value", command.getValue());
        data.put("timeout", command.getTimeout() != null ? command.getTimeout() : 10000);
        data.put("waitForNavigation", command.getWaitForNavigation() != null ? command.getWaitForNavigation() : false);
        data.put("taskId", command.getTaskId());
        data.put("stepId", command.getStepId());

        WebSocketMessage msg = WebSocketMessage.builder()
                .type("EXECUTE_COMMAND")
                .taskId(command.getTaskId())
                .stepId(command.getStepId())
                .data(data)
                .build();

        try {
            session.sendMessage(new TextMessage(JSON.toJSONString(msg)));
            log.info("✅ 指令已发送 [{}]: {} -> {}", browserSessionId, command.getAction(), command.getTarget());
        } catch (Exception e) {
            log.error("❌ 发送指令失败: {}", e.getMessage(), e);
            throw new RuntimeException("发送指令失败: " + e.getMessage(), e);
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            session.sendMessage(new TextMessage(JSON.toJSONString(message)));
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }

    private void sendError(WebSocketSession session, String error) {
        sendMessage(session, WebSocketMessage.builder()
                .type("ERROR")
                .data(Map.of("message", error))
                .build());
    }
}