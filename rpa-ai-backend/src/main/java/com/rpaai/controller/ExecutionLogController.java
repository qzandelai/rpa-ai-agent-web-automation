package com.rpaai.controller;

import com.rpaai.entity.mongodb.ExecutionLogDocument;
import com.rpaai.service.ExecutionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@CrossOrigin(origins = "*")
public class ExecutionLogController {

    @Autowired
    private ExecutionLogService logService;

    /**
     * 获取任务执行历史
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<ExecutionLogDocument>> getTaskLogs(@PathVariable Long taskId) {
        return ResponseEntity.ok(logService.getTaskHistory(taskId));
    }

    /**
     * 获取最近执行记录
     */
    @GetMapping("/recent")
    public ResponseEntity<List<ExecutionLogDocument>> getRecentLogs(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(logService.getRecentExecutions(limit));
    }

    /**
     * 获取任务执行统计
     */
    @GetMapping("/stats/{taskId}")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long taskId) {
        return ResponseEntity.ok(logService.getExecutionStats(taskId));
    }
}