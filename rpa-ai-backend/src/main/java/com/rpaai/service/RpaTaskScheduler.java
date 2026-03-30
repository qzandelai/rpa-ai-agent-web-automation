package com.rpaai.service;

import com.rpaai.core.rpa.RpaExecutionResult;
import com.rpaai.core.rpa.RpaStepResult;
import com.rpaai.entity.*;
import com.rpaai.entity.mongodb.ExecutionLogDocument;
import com.rpaai.event.*;
import com.rpaai.websocket.AgentCommand;
import com.rpaai.websocket.BrowserAgentHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RpaTaskScheduler {

    @Autowired
    private BrowserAgentHandler browserHandler;

    @Autowired
    private BrowserSessionManager sessionManager;

    @Autowired
    private AiParsingService aiParsingService;

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    @Autowired
    private ExecutionLogService executionLogService;

    @Autowired
    private ImageLocatorService imageLocatorService;

    @Autowired
    private DataExportService dataExportService;

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
                } else {
                    // 如果是extract操作且成功，保存提取的数据
                    if ("extract".equals(step.getAction())) {
                        saveExtractedData(executionId, task, context, step, result);
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

    /**
     * 保存提取的数据到MongoDB（Fastjson2兼容版）
     */
    private void saveExtractedData(String executionId, AutomationTask task, TaskExecutionContext context,
                                   RpaStep step, StepResult result) {
        try {
            String extractedData = result.getMessage();
            if (extractedData == null || extractedData.trim().isEmpty()) {
                log.warn("⚠️ 步骤 {} 提取的数据为空，跳过保存", step.getStepId());
                return;
            }

            List<String> headers;
            List<Map<String, Object>> rows = new ArrayList<>();

            try {
                String data = extractedData.trim();

                if (data.startsWith("{")) {
                    // 单个 JSON 对象
                    com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(data);
                    headers = new ArrayList<>(json.keySet());

                    Map<String, Object> row = new HashMap<>();
                    for (String key : json.keySet()) {
                        row.put(key, json.get(key));
                    }
                    rows.add(row);
                    log.info("📊 解析为单个JSON对象，字段数: {}", headers.size());

                } else if (data.startsWith("[")) {
                    // JSON 数组
                    com.alibaba.fastjson2.JSONArray array = com.alibaba.fastjson2.JSON.parseArray(data);
                    if (!array.isEmpty()) {
                        Object first = array.get(0);
                        if (first instanceof com.alibaba.fastjson2.JSONObject) {
                            com.alibaba.fastjson2.JSONObject firstObj = (com.alibaba.fastjson2.JSONObject) first;
                            headers = new ArrayList<>(firstObj.keySet());

                            for (int i = 0; i < array.size(); i++) {
                                com.alibaba.fastjson2.JSONObject obj = array.getJSONObject(i);
                                Map<String, Object> row = new HashMap<>();
                                for (String key : obj.keySet()) {
                                    row.put(key, obj.get(key));
                                }
                                rows.add(row);
                            }
                            log.info("📊 解析为JSON数组，共 {} 条", rows.size());
                        } else {
                            // 简单值数组
                            headers = Arrays.asList("value", "index");
                            for (int i = 0; i < array.size(); i++) {
                                Map<String, Object> row = new HashMap<>();
                                row.put("value", array.get(i));
                                row.put("index", i);
                                rows.add(row);
                            }
                            log.info("📊 解析为简单数组，共 {} 条", rows.size());
                        }
                    } else {
                        headers = Arrays.asList("content");
                        Map<String, Object> row = new HashMap<>();
                        row.put("content", "空数组");
                        rows.add(row);
                    }

                } else {
                    // 纯文本
                    headers = Arrays.asList("content", "source_url", "extract_time");
                    Map<String, Object> row = new HashMap<>();
                    row.put("content", data);
                    row.put("source_url", context.getCurrentUrl());
                    row.put("extract_time", java.time.LocalDateTime.now().toString());
                    rows.add(row);
                    log.info("📄 存储为纯文本，长度: {}", data.length());
                }

            } catch (Exception e) {
                log.warn("JSON解析失败，按纯文本存储: {}", e.getMessage());
                headers = Arrays.asList("content", "source_url", "selector");
                Map<String, Object> row = new HashMap<>();
                row.put("content", extractedData);
                row.put("source_url", context.getCurrentUrl());
                row.put("selector", step.getTarget());
                rows.add(row);
            }

            dataExportService.saveExtractedData(
                    task.getId(),
                    task.getTaskName(),
                    executionId,
                    context.getCurrentUrl(),
                    step.getTarget(),
                    headers,
                    rows
            );
            log.info("💾 提取的数据已保存到MongoDB，任务: {}，共 {} 条记录", executionId, rows.size());

        } catch (Exception e) {
            log.error("❌ 保存提取数据失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private StepResult executeStepWithRetry(String executionId, String browserId,
                                            RpaStep step, TaskExecutionContext context) {
        int maxRetries = step.getRetryCount() != null ? step.getRetryCount() : 3;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (context.isCancelled() || !"RUNNING".equals(context.getStatus())) {
                return StepResult.fail(step.getStepId(), "任务已取消或完成");
            }

            try {
                log.info("🎯 执行步骤 {}: action={}, target={}", step.getStepId(), step.getAction(), step.getTarget());

                AgentCommand command = AgentCommand.builder()
                        .taskId(executionId)
                        .stepId(String.valueOf(step.getStepId()))
                        .action(step.getAction())
                        .target(step.getTarget())
                        .value(step.getValue())
                        .timeout(15000)
                        .waitForNavigation(false)
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
                context.removePendingStep(String.valueOf(step.getStepId()));
                log.error("❌ 步骤 {} 尝试 {}/{} 失败: {}", step.getStepId(), attempt + 1, maxRetries, e.getMessage());

                if (attempt < maxRetries - 1) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000;
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return StepResult.fail(step.getStepId(), "被中断");
                    }

                    if (e.getMessage() != null && e.getMessage().contains("not found")
                            && step.getImageTemplate() != null && attempt == maxRetries - 2) {
                        log.info("🖼️ 尝试使用图像识别定位元素");
                        Optional<StepResult> imageResult = attemptImageLocation(executionId, browserId, step, context);
                        if (imageResult.isPresent()) {
                            return imageResult.get();
                        }
                    }

                    if (step.getFallbackTarget() != null) {
                        step.setTarget(step.getFallbackTarget());
                    }
                } else {
                    return StepResult.fail(step.getStepId(), "步骤执行失败: " + e.getMessage());
                }
            }
        }

        return StepResult.fail(step.getStepId(), "超过最大重试次数");
    }

    private Optional<StepResult> attemptImageLocation(String executionId, String browserId,
                                                      RpaStep step, TaskExecutionContext context) {
        try {
            AgentCommand screenshotCmd = AgentCommand.builder()
                    .taskId(executionId)
                    .stepId(String.valueOf(step.getStepId()))
                    .action("screenshot")
                    .timeout(5000)
                    .build();

            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            context.registerPendingStep(step.getStepId(), future);
            browserHandler.sendCommand(browserId, screenshotCmd);

            Map<String, Object> result = future.get(5, TimeUnit.SECONDS);
            String base64Image = (String) result.get("imageData");

            if (base64Image != null) {
                Optional<int[]> coordinates = imageLocatorService.locateElement(base64Image, step.getImageTemplate());

                if (coordinates.isPresent()) {
                    int[] xy = coordinates.get();
                    log.info("🖼️ 图像识别成功，坐标: ({}, {})", xy[0], xy[1]);

                    AgentCommand clickCmd = AgentCommand.builder()
                            .taskId(executionId)
                            .stepId(String.valueOf(step.getStepId()))
                            .action("click_by_coordinates")
                            .target(xy[0] + "," + xy[1])
                            .timeout(5000)
                            .build();

                    CompletableFuture<Map<String, Object>> clickFuture = new CompletableFuture<>();
                    context.registerPendingStep(step.getStepId(), clickFuture);
                    browserHandler.sendCommand(browserId, clickCmd);

                    Map<String, Object> clickResult = clickFuture.get(5, TimeUnit.SECONDS);
                    if ((boolean) clickResult.get("success")) {
                        return Optional.of(StepResult.success(step.getStepId(), "通过图像识别定位并点击成功"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("图像识别定位失败: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @EventListener
    public void onPageChanged(PageChangedEvent event) {
        runningTasks.values().stream()
                .filter(t -> event.getBrowserSessionId().equals(t.getBrowserSessionId()))
                .forEach(t -> t.setCurrentUrl(event.getUrl()));
    }

    @EventListener
    public void onElementLocated(ElementLocatedEvent event) {
        TaskExecutionContext context = runningTasks.get(event.getTaskId());
        if (context != null) {
            CompletableFuture<Map<String, Object>> future = context.getPendingStep(event.getStepId());
            if (future != null) {
                future.complete(Map.of(
                        "success", event.isFound(),
                        "message", event.isFound() ? "元素已找到" : "元素未找到",
                        "data", event.getData() != null ? event.getData() : new HashMap<>()
                ));
            }
        }
    }

    @EventListener
    public void onStepCompleted(StepCompletedEvent event) {
        TaskExecutionContext context = runningTasks.get(event.getTaskId());
        if (context != null) {
            CompletableFuture<Map<String, Object>> future = context.removePendingStep(event.getStepId());
            if (future != null) {
                future.complete(Map.of(
                        "success", event.isSuccess(),
                        "message", event.getData() != null ? event.getData().get("message") : "",
                        "data", event.getData() != null ? event.getData() : new HashMap<>()
                ));
            }
        }
    }

    @EventListener
    public void onStepFailed(StepFailedEvent event) {
        TaskExecutionContext context = runningTasks.get(event.getTaskId());
        if (context != null) {
            CompletableFuture<Map<String, Object>> future = context.removePendingStep(event.getStepId());
            if (future != null) {
                future.completeExceptionally(new RuntimeException(event.getError()));
            }
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

        return Optional.empty();
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
        fixed.setImageTemplate(original.getImageTemplate());
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