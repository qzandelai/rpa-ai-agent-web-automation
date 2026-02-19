package com.rpaai.controller;

import com.rpaai.core.rpa.RpaExecutionEngine;
import com.rpaai.core.rpa.RpaExecutionResult;
import com.rpaai.entity.RpaStep;
import com.rpaai.service.RpaExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/execution")
@CrossOrigin(origins = "*")
public class ExecutionController {

    @Autowired
    private RpaExecutionService executionService;

    @Autowired
    private RpaExecutionEngine executionEngine;

    /**
     * 执行指定ID的任务
     */
    @PostMapping("/task/{taskId}")
    public ResponseEntity<RpaExecutionResult> executeTask(@PathVariable("taskId") Long taskId) {
        log.info("收到执行请求，任务ID: {}", taskId);

        try {
            RpaExecutionResult result = executionService.executeTask(taskId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("执行失败", e);
            RpaExecutionResult errorResult = new RpaExecutionResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return ResponseEntity.ok(errorResult);
        }
    }

    /**
     * 直接执行步骤列表（测试用）
     */
    @PostMapping("/steps")
    public ResponseEntity<RpaExecutionResult> executeSteps(@RequestBody List<RpaStep> steps) {
        log.info("收到直接执行请求，步骤数: {}", steps.size());

        RpaExecutionResult result = executionService.executeSteps(steps);
        return ResponseEntity.ok(result);
    }

    /**
     * 关闭浏览器（释放资源）
     */
    @PostMapping("/close")
    public ResponseEntity<String> closeBrowser() {
        executionEngine.closeBrowser();
        return ResponseEntity.ok("浏览器已关闭");
    }
}