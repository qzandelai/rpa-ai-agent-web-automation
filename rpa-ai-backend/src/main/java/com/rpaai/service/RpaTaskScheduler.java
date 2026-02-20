package com.rpaai.service;

import com.rpaai.entity.*;
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

    @Autowired
    public RpaTaskScheduler(
            @Lazy BrowserAgentHandler browserHandler,  // @Lazy 在这里
            BrowserSessionManager sessionManager,
            AiParsingService aiParsingService,
            KnowledgeGraphService knowledgeGraphService) {
        this.browserHandler = browserHandler;
        this.sessionManager = sessionManager;
        this.aiParsingService = aiParsingService;
        this.knowledgeGraphService = knowledgeGraphService;
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

        log.info("🚀 开始执行任务 [{}] 使用浏览器 [{}]", executionId, browserId);

        TaskExecutionContext context = new TaskExecutionContext();
        context.setExecutionId(executionId);
        context.setBrowserSessionId(browserId);
        context.setTask(scheduledTask.getTask());
        context.setStartTime(System.currentTimeMillis());
        context.setStatus("RUNNING");

        runningTasks.put(executionId, context);

        try {
            List<RpaStep> steps = aiParsingService.parseSteps(scheduledTask.getTask().getConfigJson());
            context.setSteps(steps);
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

                if (!result.isSuccess()) {
                    Optional<StepResult> fixed = attemptAutoFix(executionId, browserId, step, result, context);
                    if (fixed.isPresent()) {
                        result = fixed.get();
                    } else {
                        throw new RuntimeException("步骤执行失败且无法修复: " + result.getError());
                    }
                }

                context.getCompletedSteps().add(result);
                log.info("✅ 步骤 {}/{} 完成: {}", i + 1, steps.size(), step.getDescription());
            }

            context.setStatus("COMPLETED");
            log.info("🎉 任务 [{}] 执行完成，共 {} 步", executionId, steps.size());

        } catch (Exception e) {
            context.setStatus("FAILED");
            context.setErrorMessage(e.getMessage());
            log.error("❌ 任务 [{}] 执行失败: {}", executionId, e.getMessage());
        } finally {
            runningTasks.remove(executionId);
        }
    }

    private StepResult executeStepWithRetry(String executionId, String browserId,
                                            RpaStep step, TaskExecutionContext context) {
        int maxRetries = step.getRetryCount() != null ? step.getRetryCount() : 3;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                AgentCommand command = AgentCommand.builder()
                        .taskId(executionId)
                        .stepId(String.valueOf(step.getStepId()))
                        .action(step.getAction())
                        .target(step.getTarget())
                        .value(step.getValue())
                        .timeout(10000)
                        .build();

                CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
                context.registerPendingStep(step.getStepId(), future);

                browserHandler.sendCommand(browserId, command);

                Map<String, Object> result = future.get(15, TimeUnit.SECONDS);

                boolean success = (boolean) result.get("success");
                if (success) {
                    return StepResult.success(step.getStepId(), (String) result.get("message"));
                } else {
                    throw new RuntimeException((String) result.get("error"));
                }

            } catch (Exception e) {
                log.warn("⚠️ 步骤 {} 尝试 {}/{} 失败: {}", step.getStepId(), attempt + 1, maxRetries, e.getMessage());

                if (attempt < maxRetries - 1) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000;
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return StepResult.fail(step.getStepId(), "被中断");
                    }

                    if (e.getMessage() != null &&
                            e.getMessage().contains("not found") &&
                            step.getFallbackTarget() != null) {
                        step.setTarget(step.getFallbackTarget());
                        log.info("🔄 尝试备选定位: {}", step.getTarget());
                    }
                } else {
                    return StepResult.fail(step.getStepId(), e.getMessage());
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

    public void onPageChanged(String browserSessionId, String newUrl) {
        runningTasks.values().stream()
                .filter(t -> browserSessionId.equals(t.getBrowserSessionId()))
                .forEach(t -> t.setCurrentUrl(newUrl));
    }

    public void onElementLocated(String taskId, String stepId, boolean found, Map<String, Object> data) {
        TaskExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            CompletableFuture<Map<String, Object>> future = context.getPendingStep(stepId);
            if (future != null) {
                future.complete(Map.of(
                        "success", found,
                        "message", found ? "元素已找到" : "元素未找到",
                        "data", data
                ));
            }
        }
    }

    public void onStepCompleted(String taskId, String stepId, boolean success, Map<String, Object> data) {
        TaskExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            CompletableFuture<Map<String, Object>> future = context.removePendingStep(stepId);
            if (future != null) {
                future.complete(Map.of(
                        "success", success,
                        "message", data.get("message"),
                        "data", data
                ));
            }
        }
    }

    public void onStepError(String taskId, String error, Map<String, Object> data) {
        TaskExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            String stepId = (String) data.get("stepId");
            CompletableFuture<Map<String, Object>> future = context.removePendingStep(stepId);
            if (future != null) {
                future.completeExceptionally(new RuntimeException(error));
            }
        }
    }

    private List<RpaStep> parseSteps(String configJson) {
        return aiParsingService.parseSteps(configJson);
    }

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