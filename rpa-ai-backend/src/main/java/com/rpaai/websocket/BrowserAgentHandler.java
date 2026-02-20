package com.rpaai.websocket;

import com.alibaba.fastjson2.JSON;
import com.rpaai.entity.BrowserSession;
import com.rpaai.service.BrowserSessionManager;
import com.rpaai.service.RpaTaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BrowserAgentHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> browserSessions = new ConcurrentHashMap<>();

    @Autowired
    private BrowserSessionManager sessionManager;

    @Autowired
    private RpaTaskScheduler rpaTaskScheduler;

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

        log.info("📝 收到注册请求: userId={}, fingerprint={}", userId, fingerprint);  // 添加日志

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
        rpaTaskScheduler.onPageChanged(session.getId(), url);
    }

    private void handleElementFound(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        rpaTaskScheduler.onElementLocated(
                (String) data.get("taskId"),
                (String) data.get("stepId"),
                (boolean) data.get("found"),
                data
        );
    }

    private void handleActionResult(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        rpaTaskScheduler.onStepCompleted(
                (String) data.get("taskId"),
                (String) data.get("stepId"),
                (boolean) data.get("success"),
                data
        );
    }

    private void handleBrowserError(WebSocketSession session, WebSocketMessage msg) {
        Map<String, Object> data = msg.getData();
        rpaTaskScheduler.onStepError(
                (String) data.get("taskId"),
                (String) data.get("error"),
                data
        );
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

        WebSocketMessage msg = WebSocketMessage.builder()
                .type("EXECUTE_COMMAND")
                .taskId(command.getTaskId())
                .stepId(command.getStepId())
                .data(Map.of(
                        "action", command.getAction(),
                        "target", command.getTarget(),
                        "value", command.getValue(),
                        "timeout", command.getTimeout()
                ))
                .build();

        sendMessage(session, msg);
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