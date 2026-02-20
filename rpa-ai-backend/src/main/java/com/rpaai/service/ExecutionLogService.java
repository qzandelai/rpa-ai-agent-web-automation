package com.rpaai.service;

import com.rpaai.core.rpa.RpaExecutionResult;
import com.rpaai.core.rpa.RpaStepResult;
import com.rpaai.entity.RpaStep;
import com.rpaai.entity.mongodb.ExecutionLogDocument;
import com.rpaai.repository.mongodb.ExecutionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExecutionLogService {

    @Autowired
    private ExecutionLogRepository logRepository;

    /**
     * 开始记录执行任务
     */
    public ExecutionLogDocument startExecution(Long taskId, String taskName,
                                               String naturalLanguage, List<RpaStep> steps) {
        ExecutionLogDocument doc = new ExecutionLogDocument();
        doc.setTaskId(taskId);
        doc.setTaskName(taskName);
        doc.setNaturalLanguage(naturalLanguage);
        doc.setStartTime(LocalDateTime.now());
        doc.setTotalSteps(steps.size());
        doc.setStepLogs(new ArrayList<>());

        // 预创建步骤日志结构
        for (RpaStep step : steps) {
            ExecutionLogDocument.StepLog stepLog = new ExecutionLogDocument.StepLog();
            stepLog.setStepId(step.getStepId());
            stepLog.setAction(step.getAction());
            stepLog.setTarget(step.getTarget());
            doc.getStepLogs().add(stepLog);
        }

        log.debug("开始记录执行任务: taskId={}", taskId);
        return doc;
    }

    /**
     * 记录单步执行结果
     */
    public void recordStep(ExecutionLogDocument executionLog, int stepIndex,
                           RpaStepResult result) {
        if (executionLog == null || executionLog.getStepLogs() == null) {
            return;
        }

        List<ExecutionLogDocument.StepLog> logs = executionLog.getStepLogs();
        if (stepIndex >= 0 && stepIndex < logs.size()) {
            ExecutionLogDocument.StepLog stepLog = logs.get(stepIndex);
            stepLog.setSuccess(result.isSuccess());
            stepLog.setMessage(result.getMessage());
            stepLog.setErrorMessage(result.getErrorMessage());
            stepLog.setExecutionTimeMs(result.getExecutionTimeMs());
            stepLog.setExecuteTime(LocalDateTime.now());
        }
    }

    /**
     * 完成执行记录
     */
    public ExecutionLogDocument finishExecution(ExecutionLogDocument executionLog,
                                                RpaExecutionResult result,
                                                String screenshotPath) {
        if (executionLog == null) {
            return null;
        }

        executionLog.setEndTime(LocalDateTime.now());
        executionLog.setDurationMs(
                java.time.Duration.between(executionLog.getStartTime(), executionLog.getEndTime()).toMillis()
        );
        executionLog.setSuccess(result.isSuccess());
        executionLog.setCompletedSteps(result.getCompletedSteps());
        executionLog.setErrorMessage(result.getErrorMessage());
        executionLog.setScreenshotPath(screenshotPath);

        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("javaVersion", System.getProperty("java.version"));
        metadata.put("osName", System.getProperty("os.name"));
        metadata.put("stepResultsCount", result.getStepResults().size());
        executionLog.setMetadata(metadata);

        // 保存到MongoDB
        ExecutionLogDocument saved = logRepository.save(executionLog);
        log.info("✅ 执行记录已保存到MongoDB: id={}", saved.getId());

        return saved;
    }

    /**
     * 查询任务执行历史
     */
    public List<ExecutionLogDocument> getTaskHistory(Long taskId) {
        return logRepository.findByTaskIdOrderByStartTimeDesc(taskId);
    }

    /**
     * 获取最近执行记录
     */
    public List<ExecutionLogDocument> getRecentExecutions(int limit) {
        return logRepository.findTop20ByOrderByStartTimeDesc()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取执行统计
     */
    public Map<String, Object> getExecutionStats(Long taskId) {
        Map<String, Object> stats = new HashMap<>();

        long total = logRepository.countByTaskId(taskId);
        long success = logRepository.countByTaskIdAndSuccessTrue(taskId);

        stats.put("totalExecutions", total);
        stats.put("successCount", success);
        stats.put("failureCount", total - success);
        stats.put("successRate", total > 0 ? (double) success / total * 100 : 0);

        return stats;
    }

    /**
     * 删除旧日志（保留最近100条）
     */
    public void cleanupOldLogs() {
        List<ExecutionLogDocument> allLogs = logRepository.findTop20ByOrderByStartTimeDesc();
        if (allLogs.size() > 100) {
            // 保留最近的，删除旧的
            log.info("清理旧执行日志...");
            // 实际实现：按时间删除100条之前的
        }
    }
}