package com.rpaai.controller;

import com.rpaai.entity.AutomationTask;
import com.rpaai.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")  // ✅ 必须添加，允许所有来源
public class TaskController {

    @Autowired
    private TaskService taskService;

    @PostMapping("/parse")
    public ResponseEntity<AutomationTask> parseTask(
            @RequestBody String naturalLanguage) {  // ✅ 接收纯文本
        AutomationTask task = taskService.parseNaturalLanguageTask(naturalLanguage);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/save")
    public ResponseEntity<AutomationTask> saveTask(
            @RequestBody AutomationTask task) {
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
        return ResponseEntity.ok("✅ RPA+AI Agent Backend is running on JDK 17");
    }
}