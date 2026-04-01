package com.rpaai.controller;

import com.rpaai.entity.TaskExecutionStatus;  // ✅ 添加这行
import com.rpaai.service.BrowserSessionManager;
import com.rpaai.service.RpaTaskScheduler;
import com.rpaai.service.TaskService;
import com.rpaai.service.TaskPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/execution")
@CrossOrigin(origins = "*")
public class ExecutionController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RpaTaskScheduler rpaTaskScheduler;

    @Autowired
    private BrowserSessionManager browserSessionManager;

    @PostMapping("/task/{taskId}")
    public ResponseEntity<?> submitTask(@PathVariable("taskId") Long taskId,
                                        @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {
        log.info("收到任务提交请求，任务ID: {}", taskId);

        try {
            // 检查浏览器连接
            var sessions = browserSessionManager.getAllSessions();
            if (sessions.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "没有可用的浏览器扩展连接",
                        "message", "请确保Chrome扩展已安装并连接"
                ));
            }

            String executionId = taskService.submitTaskToScheduler(taskId, userId, TaskPriority.NORMAL);

            // 关键：立即返回执行ID，不要等待执行完成
            return ResponseEntity.ok(Map.of(
                    "executionId", executionId,
                    "status", "QUEUED",
                    "message", "任务已提交，正在执行"
            ));

        } catch (Exception e) {
            log.error("提交任务失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @PostMapping("/task/{taskId}/immediate")
    public ResponseEntity<?> executeImmediate(@PathVariable("taskId") Long taskId,
                                              @RequestParam(value = "userId", defaultValue = "anonymous") String userId) {
        log.info("收到立即执行请求，任务ID: {}", taskId);

        try {
            String executionId = taskService.executeImmediately(taskId, userId);
            rpaTaskScheduler.executeImmediately(executionId);

            return ResponseEntity.ok(Map.of(
                    "executionId", executionId,
                    "status", "EXECUTING",
                    "message", "任务正在执行中"
            ));
        } catch (Exception e) {
            log.error("执行任务失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/task/{executionId}/status")
    public ResponseEntity<?> getTaskStatus(@PathVariable("executionId") String executionId) {
        TaskExecutionStatus status = rpaTaskScheduler.getTaskStatus(executionId);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    @PostMapping("/task/{executionId}/cancel")
    public ResponseEntity<?> cancelTask(@PathVariable("executionId") String executionId) {
        rpaTaskScheduler.cancelTask(executionId);
        return ResponseEntity.ok(Map.of("message", "任务已取消"));
    }

    @GetMapping("/browser-status")
    public ResponseEntity<?> getBrowserStatus() {
        var sessions = browserSessionManager.getAllSessions();
        boolean online = sessions.stream().anyMatch(s -> "ACTIVE".equals(s.getStatus()));
        return ResponseEntity.ok(Map.of(
                "online", online,
                "count", sessions.size()
        ));
    }
}