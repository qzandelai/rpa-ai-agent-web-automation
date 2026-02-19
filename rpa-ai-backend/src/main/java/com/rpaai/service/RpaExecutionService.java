package com.rpaai.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.rpaai.core.rpa.RpaExecutionEngine;
import com.rpaai.core.rpa.RpaExecutionResult;
import com.rpaai.entity.AutomationTask;
import com.rpaai.entity.RpaStep;
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

    /**
     * 执行指定ID的任务
     */
    public RpaExecutionResult executeTask(Long taskId) {
        log.info("🎯 开始执行任务 ID: {}", taskId);

        // 查询任务
        AutomationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        // 解析步骤
        List<RpaStep> steps = parseSteps(task.getConfigJson());

        // 执行任务
        RpaExecutionResult result = executionEngine.executeTask(steps);

        // 保存执行记录（可选）
        // ...

        return result;
    }

    /**
     * 直接执行步骤列表（用于测试）
     */
    public RpaExecutionResult executeSteps(List<RpaStep> steps) {
        return executionEngine.executeTask(steps);
    }

    /**
     * 解析JSON为步骤列表
     */
    private List<RpaStep> parseSteps(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            throw new RuntimeException("任务配置为空");
        }

        try {
            // 解析外层JSON获取steps数组
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(configJson);
            return json.getList("steps", RpaStep.class);
        } catch (Exception e) {
            log.error("解析步骤失败: {}", configJson, e);
            throw new RuntimeException("解析任务步骤失败: " + e.getMessage());
        }
    }
}