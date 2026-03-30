package com.rpaai.service;

import com.rpaai.core.rpa.RpaExecutionResult;
import com.rpaai.core.rpa.RpaStepResult;
import com.rpaai.entity.*;
import com.rpaai.entity.mongodb.ExecutionLogDocument;
import com.rpaai.websocket.AgentCommand;
import com.rpaai.websocket.BrowserAgentHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RpaTaskScheduler {
    private final BrowserAgentHandler browserHandler;
    private final BrowserSessionManager sessionManager;
    private final AiParsingService aiParsingService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final ExecutionLogService executionLogService;

    @Autowired
    public RpaTaskScheduler(
            @Lazy BrowserAgentHandler browserHandler,
            BrowserSessionManager sessionManager,
            AiParsingService aiParsingService,
            KnowledgeGraphService knowledgeGraphService,
            ExecutionLogService executionLogService) {
        this.browserHandler = browserHandler;
        this.sessionManager = sessionManager;
        this.aiParsingService = aiParsingService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.executionLogService = executionLogService;
    }

    private final PriorityBlockingQueue<ScheduledTask> taskQueue =
            new PriorityBlockingQueue<>(100, Comparator.comparingInt(ScheduledTask::getPriority).reversed());

    private final ConcurrentHashMap<String, TaskExecutionContext> runningTasks = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public String submitTask(AutomationTask task, String userId, TaskPriority priority) {
        String executionId = "TASK_" + System.currentTimeMillis() + "_" + new Random().nextInt(1000);

        ScheduledTask scheduledTask = ScheduledTask.builder()
                .executionId(executionId)
                .task(task)
                .userId(userId)
                .priority(priority.getValue())
                .submitTime(System.currentTimeMillis())
                .status("PENDING")
                .build();

        taskQueue.offer(scheduledTask);
        log.info("📥 任务已提交 [{}]: {}, 优先级={}", executionId, task.getTaskName(), priority);

        tryScheduleTasks();

        return executionId;
    }

    @Async
    public void executeImmediately(String executionId) {
        ScheduledTask task = findTaskInQueue(executionId);
        if (task != null) {
            taskQueue.remove(task);
            executor.submit(() -> executeTask(task));
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void tryScheduleTasks() {
        Set<String> busyBrowsers = runningTasks.values().stream()
                .map(TaskExecutionContext::getBrowserSessionId)
                .collect(Collectors.toSet());

        List<ScheduledTask> toExecute = new ArrayList<>();

        for (ScheduledTask task : taskQueue) {
            if (!"PENDING".equals(task.getStatus())) continue;

            Optional<BrowserSession> session = sessionManager.getAvailableSession(task.getUserId());
            if (session.isPresent() && !busyBrowsers.contains(session.get().getWebsocketSessionId())) {
                task.setAssignedBrowser(session.get().getWebsocketSessionId());
                task.setStatus("ASSIGNED");
                toExecute.add(task);
                busyBrowsers.add(session.get().getWebsocketSessionId());
            }
        }

        toExecute.forEach(task -> {
            taskQueue.remove(task);
            executor.submit(() -> executeTask(task));
        });
    }

    private void executeTask(ScheduledTask scheduledTask) {
        String executionId = scheduledTask.getExecutionId();
        String browserId = scheduledTask.getAssignedBrowser();
        AutomationTask task = scheduledTask.getTask();

        log.info("🚀 开始执行任务 [{}] 使用浏览器 [{}]", executionId, browserId);

        List<RpaStep> steps = aiParsingService.parseSteps(task.getConfigJson());
        if (task.getCredentialsId() != null) {
            log.info("🔐 任务关联凭据ID: {}，开始替换占位符", task.getCredentialsId());
            steps = aiParsingService.resolveCredentials(steps);
        }
        ExecutionLogDocument executionLog = executionLogService.startExecution(
                task.getId(),
                task.getTaskName(),
                task.getDescription(),
                steps
        );

        TaskExecutionContext context = new TaskExecutionContext();
        context.setExecutionId(executionId);
        context.setBrowserSessionId(browserId);
        context.setTask(task);
        context.setSteps(steps);
        context.setStartTime(System.currentTimeMillis());
        context.setStatus("RUNNING");

        runningTasks.put(executionId, context);

        RpaExecutionResult finalResult = new RpaExecutionResult();
        finalResult.setTotalSteps(steps.size());
        finalResult.setStepResults(new ArrayList<>());

        try {
            context.setCurrentStepIndex(0);

            for (int i = 0; i < steps.size(); i++) {
                if (context.isCancelled()) {
                    throw new InterruptedException("任务被取消");
                }

                RpaStep step = steps.get(i);
                context.setCurrentStepIndex(i);

                if (i > 0 && isPageTransitionStep(step)) {
                    waitForPageStable(browserId, 2000);
                }

                StepResult result = executeStepWithRetry(executionId, browserId, step, context);

                RpaStepResult stepResult = convertToRpaStepResult(result);
                finalResult.getStepResults().add(stepResult);
                executionLogService.recordStep(executionLog, i, stepResult);

                if (!result.isSuccess()) {
                    Optional<StepResult> fixed = attemptAutoFix(executionId, browserId, step, result, context);
                    if (fixed.isPresent()) {
                        result = fixed.get();
                        stepResult = convertToRpaStepResult(result);
                        finalResult.getStepResults().set(i, stepResult);
                        executionLogService.recordStep(executionLog, i, stepResult);
                    } else {
                        throw new RuntimeException("步骤执行失败且无法修复: " + result.getError());
                    }
                }

                context.getCompletedSteps().add(result);
                log.info("✅ 步骤 {}/{} 完成: {}", i + 1, steps.size(), step.getDescription());
            }

            context.setStatus("COMPLETED");
            finalResult.setSuccess(true);
            finalResult.setCompletedSteps(steps.size());
            log.info("🎉 任务 [{}] 执行完成，共 {} 步", executionId, steps.size());

        } catch (Exception e) {
            context.setStatus("FAILED");
            context.setErrorMessage(e.getMessage());
            finalResult.setSuccess(false);
            finalResult.setErrorMessage(e.getMessage());
            finalResult.setCompletedSteps(context.getCurrentStepIndex());
            log.error("❌ 任务 [{}] 执行失败: {}", executionId, e.getMessage());
        } finally {
            runningTasks.remove(executionId);
            executionLogService.finishExecution(executionLog, finalResult, null);
        }
    }

    private RpaStepResult convertToRpaStepResult(StepResult result) {
        RpaStepResult rpaResult = new RpaStepResult();
        rpaResult.setStepId(result.getStepId());
        rpaResult.setSuccess(result.isSuccess());
        rpaResult.setMessage(result.getMessage());
        rpaResult.setErrorMessage(result.getError());
        rpaResult.setExecutionTimeMs(result.getExecutionTimeMs() != null ? result.getExecutionTimeMs() : 0);
        return rpaResult;
    }

    private StepResult executeStepWithRetry(String executionId, String browserId,
                                            RpaStep step, TaskExecutionContext context) {
        int maxRetries = step.getRetryCount() != null ? step.getRetryCount() : 3;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (context.isCancelled() || !"RUNNING".equals(context.getStatus())) {
                log.warn("⛔ 任务 [{}] 已停止，中断重试", executionId);
                return StepResult.fail(step.getStepId(), "任务已取消或完成");
            }

            try {
                log.info("🎯 执行步骤 {}: action={}, target={}, value={}",
                        step.getStepId(), step.getAction(), step.getTarget(),
                        step.getValue() != null ? (step.getValue().length() > 20 ? step.getValue().substring(0, 20) + "..." : step.getValue()) : "null");

                AgentCommand command = AgentCommand.builder()
                        .taskId(executionId)
                        .stepId(String.valueOf(step.getStepId()))
                        .action(step.getAction())
                        .target(step.getTarget())
                        .value(step.getValue())
                        .timeout(15000)  // 增加超时到15秒
                        .waitForNavigation(false)
                        .build();

                CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
                context.registerPendingStep(step.getStepId(), future);

                log.info("📤 发送命令到浏览器 [{}]: action={}", browserId, command.getAction());
                browserHandler.sendCommand(browserId, command);
                log.info("✅ 命令已发送，等待响应（超时15秒）...");

                Map<String, Object> result = future.get(15, TimeUnit.SECONDS);
                log.info("📨 收到响应: {}", result);

                boolean success = (boolean) result.get("success");
                if (success) {
                    return StepResult.success(step.getStepId(), (String) result.get("message"));
                } else {
                    String errorMsg = (String) result.get("error");
                    throw new RuntimeException(errorMsg != null ? errorMsg : "浏览器返回失败状态");
                }

            } catch (Exception e) {
                context.removePendingStep(String.valueOf(step.getStepId()));

                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = e.getClass().getSimpleName();
                }

                log.error("❌ 步骤 {} 尝试 {}/{} 失败: {}", step.getStepId(), attempt + 1, maxRetries, errorMsg, e);

                if (attempt < maxRetries - 1) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000;
                    log.info("⏳ 等待 {}ms 后重试...", waitMs);
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return StepResult.fail(step.getStepId(), "被中断");
                    }

                    // 尝试备选选择器
                    if (errorMsg.contains("not found") && step.getFallbackTarget() != null) {
                        log.info("🔄 尝试备选定位: {}", step.getFallbackTarget());
                        step.setTarget(step.getFallbackTarget());
                    }
                } else {
                    return StepResult.fail(step.getStepId(), "步骤执行失败（" + (attempt + 1) + "次尝试）: " + errorMsg);
                }
            }
        }

        return StepResult.fail(step.getStepId(), "超过最大重试次数");
    }

    private Optional<StepResult> attemptAutoFix(String executionId, String browserId,
                                                RpaStep failedStep, StepResult failure,
                                                TaskExecutionContext context) {
        log.info("🧠 AI Agent尝试智能修复步骤 {}", failedStep.getStepId());

        Optional<String> kgSolution = knowledgeGraphService.findSolution(
                new RuntimeException(failure.getError()),
                failedStep,
                context.getCurrentUrl()
        );

        if (kgSolution.isPresent()) {
            log.info("💡 应用知识图谱方案: {}", kgSolution.get());
            RpaStep fixedStep = applyKnowledgeFix(failedStep, kgSolution.get());
            try {
                return Optional.of(executeStepWithRetry(executionId, browserId, fixedStep, context));
            } catch (Exception e) {
                log.error("知识图谱方案也失败", e);
            }
        }

        if (shouldReplan(failure)) {
            log.info("🔄 触发AI动态重规划");
            List<RpaStep> newPlan = aiParsingService.replanSteps(
                    context.getTask().getDescription(),
                    context.getCompletedSteps(),
                    failure,
                    context.getCurrentUrl()
            );

            if (newPlan != null && !newPlan.isEmpty()) {
                context.replaceRemainingSteps(newPlan);
                return Optional.of(StepResult.success(failedStep.getStepId(), "已重规划"));
            }
        }

        return Optional.empty();
    }

    // ==================== 浏览器事件回调方法 ====================

    public void onPageChanged(String browserSessionId, String newUrl) {
        runningTasks.values().stream()
                .filter(t -> browserSessionId.equals(t.getBrowserSessionId()))
                .forEach(t -> t.setCurrentUrl(newUrl));
    }

    /**
     * 🆕 新增：处理元素定位结果（BrowserAgentHandler调用）
     */
    public void onElementLocated(String taskId, String stepId, boolean found, Map<String, Object> data) {
        TaskExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            String stepIdStr = stepId != null ? String.valueOf(stepId) : null;
            if (stepIdStr == null) {
                log.error("❌ onElementLocated: stepId为null");
                return;
            }

            CompletableFuture<Map<String, Object>> future = context.getPendingStep(stepIdStr);
            if (future != null) {
                future.complete(Map.of(
                        "success", found,
                        "message", found ? "元素已找到" : "元素未找到",
                        "data", data != null ? data : new HashMap<>()
                ));
            } else {
                log.warn("⚠️ onElementLocated: 未找到待处理的步骤: taskId={}, stepId={}", taskId, stepId);
            }
        } else {
            log.warn("⚠️ onElementLocated: 任务上下文不存在: taskId={}", taskId);
        }
    }

    /**
     * 🆕 新增：处理步骤完成（BrowserAgentHandler调用）
     */
    public void onStepCompleted(String taskId, String stepId, boolean success, Map<String, Object> data) {
        TaskExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            String stepIdStr = stepId != null ? String.valueOf(stepId) : null;
            if (stepIdStr == null) {
                log.error("❌ onStepCompleted: stepId为null");
                return;
            }

            CompletableFuture<Map<String, Object>> future = context.removePendingStep(stepIdStr);
            if (future != null) {
                future.complete(Map.of(
                        "success", success,
                        "message", data != null ? data.get("message") : "",
                        "data", data != null ? data : new HashMap<>()
                ));
            } else {
                log.warn("⚠️ onStepCompleted: 未找到待处理的步骤: taskId={}, stepId={}", taskId, stepId);
            }
        } else {
            log.warn("⚠️ onStepCompleted: 任务上下文不存在: taskId={}", taskId);
        }
    }

    /**
     * 🆕 新增：处理步骤错误（BrowserAgentHandler调用）
     */
    public void onStepError(String taskId, String error, Map<String, Object> data) {
        TaskExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            String stepId = data != null ? String.valueOf(data.get("stepId")) : null;
            if (stepId != null && !"null".equals(stepId)) {
                CompletableFuture<Map<String, Object>> future = context.removePendingStep(stepId);
                if (future != null) {
                    future.completeExceptionally(new RuntimeException(error != null ? error : "未知错误"));
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    private boolean isPageTransitionStep(RpaStep step) {
        return "open_url".equals(step.getAction()) || "click".equals(step.getAction());
    }

    private void waitForPageStable(String browserId, long timeoutMs) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldReplan(StepResult failure) {
        return failure.getError() != null &&
                (failure.getError().contains("流程中断") ||
                        failure.getError().contains("页面结构变化"));
    }

    private RpaStep applyKnowledgeFix(RpaStep original, String solution) {
        RpaStep fixed = new RpaStep();
        fixed.setStepId(original.getStepId());
        fixed.setAction(original.getAction());
        fixed.setDescription(original.getDescription() + " [修复]");
        fixed.setRetryCount(1);

        if (solution.contains("备选定位:")) {
            String newTarget = solution.substring(solution.indexOf(":") + 1).trim();
            fixed.setTarget(newTarget);
        } else {
            fixed.setTarget(original.getTarget());
        }

        fixed.setValue(original.getValue());
        fixed.setWaitTime(original.getWaitTime());
        return fixed;
    }

    private ScheduledTask findTaskInQueue(String executionId) {
        return taskQueue.stream()
                .filter(t -> t.getExecutionId().equals(executionId))
                .findFirst()
                .orElse(null);
    }

    public void cancelTask(String executionId) {
        TaskExecutionContext context = runningTasks.get(executionId);
        if (context != null) {
            context.setCancelled(true);
            log.info("🛑 任务 [{}] 已标记取消", executionId);
        }
    }

    public TaskExecutionStatus getTaskStatus(String executionId) {
        TaskExecutionContext context = runningTasks.get(executionId);
        if (context != null) {
            return TaskExecutionStatus.builder()
                    .executionId(executionId)
                    .status(context.getStatus())
                    .currentStep(context.getCurrentStepIndex())
                    .totalSteps(context.getSteps().size())
                    .currentUrl(context.getCurrentUrl())
                    .errorMessage(context.getErrorMessage())
                    .startTime(context.getStartTime())
                    .durationMs(System.currentTimeMillis() - context.getStartTime())
                    .build();
        }
        return null;
    }
}