package com.rpaai.service;

import com.rpaai.entity.AutomationTask;
import com.rpaai.entity.RpaStep;
import com.rpaai.websocket.BrowserAgentHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RealTimeMonitorService {

    @Autowired
    private BrowserAgentHandler browserAgentHandler;

    /**
     * 通知任务开始
     */
    public void notifyExecutionStart(String executionId, Object taskInfo, int totalSteps) {
        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId);
        data.put("task", taskInfo);
        data.put("totalSteps", totalSteps);
        data.put("status", "RUNNING");
        data.put("currentStep", 0);
        data.put("startTime", System.currentTimeMillis());
        // 关键：添加任务基本信息，避免前端访问task.taskName时报空
        if (taskInfo instanceof AutomationTask) {
            AutomationTask task = (AutomationTask) taskInfo;
            data.put("taskName", task.getTaskName());
            data.put("description", task.getDescription());
        }

        browserAgentHandler.broadcastToFrontend("EXECUTION_START", data);
        log.info("📢 广播任务开始: {}, 总步骤: {}", executionId, totalSteps);
    }

    /**
     * 通知步骤开始
     */
    public void notifyStepStart(String executionId, int stepIndex, RpaStep step) {
        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId);
        data.put("stepIndex", stepIndex);
        data.put("step", convertStepToMap(step));
        data.put("timestamp", System.currentTimeMillis());

        browserAgentHandler.broadcastToFrontend("STEP_START", data);
        log.debug("📢 广播步骤开始: {} - 步骤 {}", executionId, stepIndex);
    }

    /**
     * 通知步骤完成
     */
    public void notifyStepComplete(String executionId, int stepIndex, String message, Object result) {
        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId);
        data.put("stepIndex", stepIndex);
        data.put("message", message);
        data.put("result", result);
        data.put("timestamp", System.currentTimeMillis());

        browserAgentHandler.broadcastToFrontend("STEP_COMPLETE", data);
        log.debug("📢 广播步骤完成: {} - 步骤 {}", executionId, stepIndex);
    }

    /**
     * 通知步骤错误
     */
    public void notifyStepError(String executionId, int stepIndex, String error) {
        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId);
        data.put("stepIndex", stepIndex);
        data.put("error", error);
        data.put("timestamp", System.currentTimeMillis());

        browserAgentHandler.broadcastToFrontend("STEP_ERROR", data);
        log.warn("📢 广播步骤错误: {} - 步骤 {}, 错误: {}", executionId, stepIndex, error);
    }

    /**
     * 通知页面变化
     */
    public void notifyPageChange(String executionId, String url, String title) {
        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId);
        data.put("url", url);
        data.put("title", title);
        data.put("domain", extractDomain(url));
        data.put("timestamp", System.currentTimeMillis());

        browserAgentHandler.broadcastToFrontend("PAGE_CHANGE", data);
        log.info("📢 广播页面变化: {} -> {}", executionId, extractDomain(url));
    }

    /**
     * 通知任务完成
     */
    public void notifyExecutionComplete(String executionId, boolean success, String message, int completedSteps) {
        Map<String, Object> data = new HashMap<>();
        data.put("executionId", executionId);
        data.put("success", success);
        data.put("message", message);
        data.put("completedSteps", completedSteps);
        data.put("endTime", System.currentTimeMillis());

        browserAgentHandler.broadcastToFrontend("EXECUTION_COMPLETE", data);
        log.info("📢 广播任务完成: {} - 成功: {}, 完成步骤: {}",
                executionId, success, completedSteps);
    }

    /**
     * 通知任务队列更新
     */
    public void notifyQueueUpdate(List<?> queue) {
        Map<String, Object> data = new HashMap<>();
        data.put("queue", queue);
        data.put("queueSize", queue.size());
        data.put("timestamp", System.currentTimeMillis());

        browserAgentHandler.broadcastToFrontend("QUEUE_UPDATE", data);
        log.debug("📢 广播队列更新: 当前 {} 个任务", queue.size());
    }

    private Map<String, Object> convertStepToMap(RpaStep step) {
        Map<String, Object> map = new HashMap<>();
        if (step != null) {
            map.put("stepId", step.getStepId());
            map.put("action", step.getAction());
            map.put("target", step.getTarget());
            map.put("description", step.getDescription());
            map.put("value", step.getValue());
            map.put("waitTime", step.getWaitTime());
        }
        return map;
    }

    private String extractDomain(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Exception e) {
            return url.length() > 30 ? url.substring(0, 30) + "..." : url;
        }
    }
}