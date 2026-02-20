package com.rpaai.service;

import com.alibaba.fastjson2.JSON;
import com.rpaai.core.rpa.RpaExecutionEngine;
import com.rpaai.core.rpa.RpaExecutionResult;
import com.rpaai.entity.AutomationTask;
import com.rpaai.entity.RpaStep;
import com.rpaai.entity.mongodb.ExecutionLogDocument;
import com.rpaai.repository.AutomationTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RpaExecutionService {

    @Autowired
    private RpaExecutionEngine executionEngine;

    @Autowired
    private AutomationTaskRepository taskRepository;

    @Autowired
    private ExecutionLogService logService;  // 新增

    /**
     * 执行指定ID的任务（带日志记录）
     */
    public RpaExecutionResult executeTask(Long taskId) {
        log.info("🎯 开始执行任务 ID: {}", taskId);

        // 查询任务
        AutomationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        // 解析步骤
        List<RpaStep> steps = parseSteps(task.getConfigJson());

        // 开始记录日志
        ExecutionLogDocument executionLog = logService.startExecution(
                taskId,
                task.getTaskName(),
                task.getDescription(),
                steps
        );

        // 执行任务
        RpaExecutionResult result;
        try {
            result = executionEngine.executeTask(steps);

            // 记录每步结果
            for (int i = 0; i < result.getStepResults().size(); i++) {
                logService.recordStep(executionLog, i, result.getStepResults().get(i));
            }

        } catch (Exception e) {
            // 执行异常
            result = new RpaExecutionResult();
            result.setSuccess(false);
            result.setErrorMessage("执行异常: " + e.getMessage());
            result.setCompletedSteps(0);
        }

        // 完成日志记录
        String screenshotPath = null; // 可以从result中获取
        logService.finishExecution(executionLog, result, screenshotPath);

        return result;
    }

    /**
     * 直接执行步骤列表（测试用，不记录日志）
     */
    public RpaExecutionResult executeSteps(List<RpaStep> steps) {
        return executionEngine.executeTask(steps);
    }

    private List<RpaStep> parseSteps(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            throw new RuntimeException("任务配置为空");
        }
        try {
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(configJson);
            return json.getList("steps", RpaStep.class);
        } catch (Exception e) {
            log.error("解析步骤失败: {}", configJson, e);
            throw new RuntimeException("解析任务步骤失败: " + e.getMessage());
        }
    }
}