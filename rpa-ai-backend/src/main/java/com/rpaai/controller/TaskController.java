package com.rpaai.controller;

import com.rpaai.entity.AutomationTask;
import com.rpaai.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务管理接口 - 修改版
 * 位置：src/main/java/com/rpaai/controller/TaskController.java
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;

    /**
     * 🆕 修改：支持凭据ID参数
     * POST /api/tasks/parse
     * 请求体：{ "description": "登录github", "credentialsId": 1 }
     */
    @PostMapping("/parse")
    public ResponseEntity<AutomationTask> parseTask(@RequestBody Map<String, Object> request) {
        String naturalLanguage = (String) request.get("description");
        Long credentialsId = request.get("credentialsId") != null ?
                Long.valueOf(request.get("credentialsId").toString()) : null;

        log.info("📝 解析任务: {}, 凭据ID: {}", naturalLanguage, credentialsId);

        AutomationTask task = taskService.parseWithAI(naturalLanguage, credentialsId);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/save")
    public ResponseEntity<AutomationTask> saveTask(@RequestBody AutomationTask task) {
        AutomationTask saved = taskService.saveTask(task);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AutomationTask> getTask(@PathVariable("id") Long id) {
        AutomationTask task = taskService.getTaskById(id);
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("✅ RPA+AI Agent Backend is running");
    }
}