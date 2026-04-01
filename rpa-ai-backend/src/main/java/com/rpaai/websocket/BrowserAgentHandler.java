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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class BrowserAgentHandler extends TextWebSocketHandler {

    // 浏览器扩展会话（任务执行者）
    private final Map<String, WebSocketSession> browserSessions = new ConcurrentHashMap<>();

    // 前端监控页面会话（观察者）- 新增
    private final CopyOnWriteArrayList<WebSocketSession> frontendSessions = new CopyOnWriteArrayList<>();

    @Autowired
    private BrowserSessionManager sessionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        log.info("🔌 WebSocket连接已建立: {}", sessionId);

        // 发送连接成功消息，等待客户端注册类型
        sendMessage(session, WebSocketMessage.builder()
                .type("CONNECTED")
                .data(Map.of(
                        "sessionId", sessionId,
                        "timestamp", System.currentTimeMillis(),
                        "message", "等待客户端注册类型"
                ))
                .build());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("📨 收到消息 [{}]: {}", session.getId(), payload);

        try {
            WebSocketMessage msg = JSON.parseObject(payload, WebSocketMessage.class);

            // 根据客户端类型分发处理
            if ("REGISTER".equals(msg.getType())) {
                handleRegister(session, msg);
            } else if ("HEARTBEAT".equals(msg.getType())) {
                handleHeartbeat(session, msg);
            } else {
                // 其他业务消息
                handleBusinessMessage(session, msg);
            }
        } catch (Exception e) {
            log.error("❌ 消息处理失败", e);
            sendError(session, "消息格式错误: " + e.getMessage());
        }
    }

    private void handleRegister(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();

        // 修复：优先从 msg.getClientType() 获取，兼容前端格式
        String clientType = msg.getClientType();
        if (clientType == null && data != null) {
            clientType = (String) data.get("clientType");
        }
        if (clientType == null) {
            clientType = "BROWSER";
        }

        if ("FRONTEND".equals(clientType)) {
            frontendSessions.add(session);
            log.info("🖥️ 前端监控页面已注册: {}, 当前前端连接数: {}",
                    session.getId(), frontendSessions.size());

            sendMessage(session, WebSocketMessage.builder()
                    .type("REGISTERED")
                    .data(Map.of("role", "frontend", "status", "success"))
                    .build());

            // 如果有浏览器已在线，立即通知前端
            boolean hasBrowserOnline = browserSessions.values().stream().anyMatch(WebSocketSession::isOpen);
            if (hasBrowserOnline) {
                sendMessage(session, WebSocketMessage.builder()
                        .type("BROWSER_STATUS")
                        .data(Map.of("online", true, "count", browserSessions.size()))
                        .build());
            }
        } else {
            // 浏览器扩展注册（原有逻辑）
            String userId = data != null ? (String) data.get("userId") : "anonymous";
            String fingerprint = data != null ? (String) data.get("fingerprint") : "unknown";

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
            browserSessions.put(session.getId(), session);

            log.info("🔌 浏览器扩展已注册: {}, 用户: {}", session.getId(), userId);

            // 广播浏览器上线状态到所有前端
            broadcastToFrontend("BROWSER_STATUS", Map.of("online", true, "count", browserSessions.size()));

            sendMessage(session, WebSocketMessage.builder()
                    .type("REGISTERED")
                    .data(Map.of(
                            "role", "browser",
                            "status", "success",
                            "sessionId", session.getId()
                    ))
                    .build());
        }
    }

    private void handleHeartbeat(WebSocketSession session, WebSocketMessage msg) {
        // 更新浏览器会话心跳
        sessionManager.updateHeartbeat(session.getId());

        // 回复心跳
        sendMessage(session, WebSocketMessage.builder()
                .type("HEARTBEAT_ACK")
                .data(Map.of(
                        "timestamp", System.currentTimeMillis(),
                        "serverTime", System.currentTimeMillis()
                ))
                .build());
    }

    private void handleBusinessMessage(WebSocketSession session, WebSocketMessage msg) {
        // 浏览器扩展的业务消息处理
        switch (msg.getType()) {
            case "PAGE_INFO" -> handlePageInfo(session, msg);
            case "ELEMENT_FOUND" -> handleElementFound(session, msg);
            case "ACTION_RESULT" -> handleActionResult(session, msg);
            case "ERROR" -> handleBrowserError(session, msg);
            default -> log.warn("⚠️ 未知消息类型: {}", msg.getType());
        }
    }

    private void handlePageInfo(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        if (data == null) return;

        String url = (String) data.get("url");
        String title = (String) data.get("title");

        sessionManager.updatePageInfo(session.getId(), url, title);

        eventPublisher.publishEvent(new PageChangedEvent(
                this, session.getId(), url, title));
    }

    private void handleElementFound(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        if (data == null) return;

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

        if (taskId == null || stepId == null) {
            log.error("❌ ACTION_RESULT缺少taskId或stepId");
            return;
        }

        Boolean success = data != null ? (Boolean) data.get("success") : false;
        log.info("✅ 处理动作结果: taskId={}, stepId={}, success={}", taskId, stepId, success);

        eventPublisher.publishEvent(new StepCompletedEvent(
                this, taskId, stepId, success != null ? success : false, data));
    }

    private void handleBrowserError(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        if (data == null) return;

        String taskId = (String) data.get("taskId");
        String error = (String) data.get("error");
        String stepId = (String) data.get("stepId");

        if (taskId != null) {
            eventPublisher.publishEvent(new StepFailedEvent(
                    this, taskId, stepId, error, data));
        }
    }

    /**
     * 广播消息到所有前端监控页面
     */
    public void broadcastToFrontend(String type, Map<String, Object> data) {
        WebSocketMessage msg = WebSocketMessage.builder()
                .type(type)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();

        String json = JSON.toJSONString(msg);
        int count = 0;

        for (WebSocketSession session : frontendSessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                    count++;
                } catch (IOException e) {
                    log.error("发送消息到前端失败: {}", e.getMessage());
                }
            }
        }

        if (count > 0) {
            log.debug("📢 广播 [{}] 到 {} 个前端客户端", type, count);
        }
    }

    /**
     * 发送命令到浏览器扩展
     */
    public void sendCommand(String browserSessionId, AgentCommand command) {
        WebSocketSession session = browserSessions.get(browserSessionId);

        if (session == null || !session.isOpen()) {
            throw new RuntimeException("浏览器会话不存在或已关闭: " + browserSessionId);
        }

        Map<String, Object> data = new java.util.HashMap<>();
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

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();

        // 检查是否是浏览器扩展
        if (browserSessions.remove(sessionId) != null) {
            sessionManager.unregisterSession(sessionId);
            log.info("🔌 浏览器扩展断开: {}", sessionId);
            // 广播浏览器离线状态
            boolean hasBrowserOnline = browserSessions.values().stream().anyMatch(WebSocketSession::isOpen);
            broadcastToFrontend("BROWSER_STATUS", Map.of("online", hasBrowserOnline, "count", browserSessions.size()));
        }

        // 检查是否是前端监控
        if (frontendSessions.remove(session)) {
            log.info("🔌 前端监控页面断开: {}, 剩余前端连接: {}",
                    sessionId, frontendSessions.size());
        }
    }

    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(JSON.toJSONString(message)));
            }
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