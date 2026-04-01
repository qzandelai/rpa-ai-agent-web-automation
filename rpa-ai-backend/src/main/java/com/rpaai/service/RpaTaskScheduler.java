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
    private AiAutoFixService aiAutoFixService;

    @Autowired
    private ExecutionLogService executionLogService;

    @Autowired
    private ImageLocatorService imageLocatorService;

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private RealTimeMonitorService monitorService;  // 实时监控服务

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

        // 关键：立即广播队列更新，包含任务基本信息
        List<Map<String, Object>> queueList = taskQueue.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("executionId", t.getExecutionId());
                    map.put("status", t.getStatus());
                    map.put("priority", t.getPriority());
                    map.put("submitTime", t.getSubmitTime());
                    map.put("taskName", t.getTask().getTaskName());  // 添加任务名
                    return map;
                })
                .collect(Collectors.toList());

        monitorService.notifyQueueUpdate(queueList);

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

        // 🔔 广播任务开始
        monitorService.notifyExecutionStart(executionId, task, steps.size());

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
                context.setCurrentStepIndex(i + 1);

                if (i > 0 && isPageTransitionStep(step)) {
                    waitForPageStable(browserId, 2000);
                }

                // 🔔 广播步骤开始
                monitorService.notifyStepStart(executionId, i + 1, step);

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
                        // 🔔 广播修复后完成
                        monitorService.notifyStepComplete(executionId, i + 1,
                                step.getDescription() + " 完成(自动修复)", result.getMessage());
                    } else {
                        throw new RuntimeException("步骤执行失败且无法修复: " + result.getError());
                    }
                } else {
                    // 🔔 广播步骤完成
                    monitorService.notifyStepComplete(executionId, i + 1,
                            step.getDescription() + " 完成", result.getMessage());

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

            // 🔔 广播任务完成
            monitorService.notifyExecutionComplete(executionId, true,
                    "任务执行成功，共 " + steps.size() + " 步", steps.size());

            log.info("🎉 任务 [{}] 执行完成，共 {} 步", executionId, steps.size());

            // 记录成功的元素模式到知识图谱
            for (RpaStep step : steps) {
                if ("click".equals(step.getAction()) || "input".equals(step.getAction())) {
                    List<String> alternatives = new ArrayList<>();
                    if (step.getFallbackTarget() != null) alternatives.add(step.getFallbackTarget());
                    knowledgeGraphService.recordElementPattern(
                            context.getCurrentUrl(),
                            step.getAction(),
                            step.getTarget(),
                            alternatives,
                            step.getImageTemplate(),
                            step.getImageThreshold()
                    );
                }
            }

        } catch (Exception e) {
            context.setStatus("FAILED");
            context.setErrorMessage(e.getMessage());
            finalResult.setSuccess(false);
            finalResult.setErrorMessage(e.getMessage());
            finalResult.setCompletedSteps(context.getCurrentStepIndex());

            // 🔔 广播任务失败
            monitorService.notifyExecutionComplete(executionId, false,
                    "任务失败: " + e.getMessage(), context.getCurrentStepIndex());

            log.error("❌ 任务 [{}] 执行失败: {}", executionId, e.getMessage());
        } finally {
            runningTasks.remove(executionId);
            executionLogService.finishExecution(executionLog, finalResult, null);
            // 🔔 广播队列更新
            monitorService.notifyQueueUpdate(new ArrayList<>(taskQueue));
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

                String commandValue = step.getValue();
                if ("wait".equals(step.getAction()) && step.getWaitTime() != null) {
                    commandValue = String.valueOf(step.getWaitTime());
                }
                
                AgentCommand command = AgentCommand.builder()
                        .taskId(executionId)
                        .stepId(String.valueOf(step.getStepId()))
                        .action(step.getAction())
                        .target(step.getTarget())
                        .value(commandValue)
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

                    // 最后一次重试前尝试图像识别
                    if (e.getMessage() != null
                            && (e.getMessage().contains("not found") || e.getMessage().contains("未找到"))
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
            log.info("🖼️ 尝试图像识别定位元素: stepId={}", step.getStepId());

            // 先获取当前滚动位置（用于后续坐标转换）
            AgentCommand scrollCmd = AgentCommand.builder()
                    .taskId(executionId)
                    .stepId(String.valueOf(step.getStepId()))
                    .action("get_scroll_position")
                    .timeout(5000)
                    .build();

            CompletableFuture<Map<String, Object>> scrollFuture = new CompletableFuture<>();
            context.registerPendingStep(step.getStepId(), scrollFuture);
            browserHandler.sendCommand(browserId, scrollCmd);

            Map<String, Object> scrollResult = scrollFuture.get(5, TimeUnit.SECONDS);
            int scrollX = scrollResult.get("scrollX") != null ? ((Number) scrollResult.get("scrollX")).intValue() : 0;
            int scrollY = scrollResult.get("scrollY") != null ? ((Number) scrollResult.get("scrollY")).intValue() : 0;

            // 截图
            AgentCommand screenshotCmd = AgentCommand.builder()
                    .taskId(executionId)
                    .stepId(String.valueOf(step.getStepId()))
                    .action("screenshot")
                    .timeout(10000) // 增加超时时间
                    .build();

            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            context.registerPendingStep(step.getStepId(), future);
            browserHandler.sendCommand(browserId, screenshotCmd);

            Map<String, Object> result = future.get(10, TimeUnit.SECONDS);
            String base64Image = (String) result.get("imageData");

            if (base64Image == null || base64Image.isEmpty()) {
                log.error("截图返回为空");
                return Optional.empty();
            }

            log.info("截图成功，开始匹配模板...");

            // 执行匹配
            Optional<int[]> coordinates = imageLocatorService.locateElement(base64Image, step.getImageTemplate());

            if (coordinates.isPresent()) {
                int[] xy = coordinates.get();
                log.info("🖼️ 图像匹配成功，绝对坐标: ({}, {})，滚动偏移: ({}, {})",
                        xy[0], xy[1], scrollX, scrollY);

                // 转换为视口坐标（减去滚动偏移）
                int viewportX = xy[0] - scrollX;
                int viewportY = xy[1] - scrollY;

                log.info("转换后的视口坐标: ({}, {})", viewportX, viewportY);

                // 执行点击（使用坐标）
                AgentCommand clickCmd = AgentCommand.builder()
                        .taskId(executionId)
                        .stepId(String.valueOf(step.getStepId()))
                        .action("click_by_coordinates")
                        .target(viewportX + "," + viewportY) // 使用视口坐标
                        .timeout(5000)
                        .build();

                CompletableFuture<Map<String, Object>> clickFuture = new CompletableFuture<>();
                context.registerPendingStep(step.getStepId(), clickFuture);
                browserHandler.sendCommand(browserId, clickCmd);

                Map<String, Object> clickResult = clickFuture.get(5, TimeUnit.SECONDS);
                if ((boolean) clickResult.get("success")) {
                    return Optional.of(StepResult.success(step.getStepId(),
                            "通过图像识别定位并点击成功，坐标: (" + viewportX + ", " + viewportY + ")"));
                }
            } else {
                log.warn("图像匹配未找到目标");
            }
        } catch (Exception e) {
            log.error("图像识别定位失败: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    @EventListener
    public void onPageChanged(PageChangedEvent event) {
        runningTasks.values().stream()
                .filter(t -> event.getBrowserSessionId().equals(t.getBrowserSessionId()))
                .forEach(t -> {
                    t.setCurrentUrl(event.getUrl());
                    // 🔔 广播页面变化
                    monitorService.notifyPageChange(t.getExecutionId(), event.getUrl(), event.getTitle());
                });
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
                Map<String, Object> result = new HashMap<>();
                result.put("success", event.isSuccess());
                result.put("message", event.getData() != null ? event.getData().get("message") : "");
                result.put("error", event.getData() != null ? event.getData().get("error") : "");
                result.put("data", event.getData() != null ? event.getData() : new HashMap<>());
                future.complete(result);
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

        // 1. 先尝试知识图谱修复（快通道）
        Optional<String> kgSolution = knowledgeGraphService.findSolution(
                new RuntimeException(failure.getError()),
                failedStep,
                context.getCurrentUrl()
        );

        if (kgSolution.isPresent()) {
            String solution = kgSolution.get();
            log.info("💡 应用知识图谱方案: {}", solution);
            RpaStep fixedStep = applyKnowledgeFix(failedStep, solution);
            try {
                StepResult fixResult = executeStepWithRetry(executionId, browserId, fixedStep, context);
                knowledgeGraphService.recordSuccessSolution(
                        new RuntimeException(failure.getError()),
                        failedStep,
                        solution,
                        context.getCurrentUrl()
                );
                return Optional.of(fixResult);
            } catch (Exception e) {
                log.error("知识图谱方案也失败", e);
            }
        }

        // 2. 知识图谱修不好，调用 LLM 进行运行时诊断（慢但智能）
        log.info("🤖 知识图谱无方案，尝试 LLM 运行时修复");
        try {
            // 2.1 获取页面上下文
            AgentCommand contextCmd = AgentCommand.builder()
                    .taskId(executionId)
                    .stepId(String.valueOf(failedStep.getStepId()))
                    .action("get_page_context")
                    .timeout(5000)
                    .build();

            CompletableFuture<Map<String, Object>> contextFuture = new CompletableFuture<>();
            context.registerPendingStep(failedStep.getStepId(), contextFuture);
            browserHandler.sendCommand(browserId, contextCmd);

            Map<String, Object> pageResult = contextFuture.get(5, TimeUnit.SECONDS);
            String pageContextJson = (String) pageResult.get("message");
            com.alibaba.fastjson2.JSONObject ctx = com.alibaba.fastjson2.JSON.parseObject(pageContextJson);
            String pageHtml = ctx.getString("html");
            String pageUrl = ctx.getString("url");

            // 2.2 LLM 诊断
            Optional<RpaStep> llmFix = aiAutoFixService.fixStep(
                    failedStep,
                    failure.getError(),
                    pageUrl != null ? pageUrl : context.getCurrentUrl(),
                    pageHtml
            );

            if (llmFix.isPresent()) {
                RpaStep fixedStep = llmFix.get();
                log.info("💡 应用 LLM 修复方案: {} -> {}", fixedStep.getAction(), fixedStep.getTarget());
                StepResult fixResult = executeStepWithRetry(executionId, browserId, fixedStep, context);

                // 记录成功修复到知识图谱，形成学习闭环
                String solution = String.format("LLM修复: %s -> %s", fixedStep.getAction(), fixedStep.getTarget());
                knowledgeGraphService.recordSuccessSolution(
                        new RuntimeException(failure.getError()),
                        failedStep,
                        solution,
                        context.getCurrentUrl()
                );
                if ("click".equals(fixedStep.getAction()) || "input".equals(fixedStep.getAction())) {
                    java.util.List<String> alts = new java.util.ArrayList<>();
                    if (failedStep.getTarget() != null) alts.add(failedStep.getTarget());
                    knowledgeGraphService.recordElementPattern(
                            context.getCurrentUrl(),
                            fixedStep.getAction(),
                            fixedStep.getTarget(),
                            alts,
                            fixedStep.getImageTemplate(),
                            fixedStep.getImageThreshold()
                    );
                }

                return Optional.of(fixResult);
            }
        } catch (Exception e) {
            log.error("❌ LLM 运行时修复失败: {}", e.getMessage(), e);
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

        if (solution.contains("视觉定位:IMG:")) {
            // 视觉定位方案：提取 imageTemplate 和 threshold
            fixed.setTarget(original.getTarget());
            String imgPart = solution.substring(solution.indexOf("IMG:") + 4);
            String base64 = imgPart;
            double threshold = 0.8;
            if (imgPart.contains(":THR:")) {
                base64 = imgPart.substring(0, imgPart.indexOf(":THR:"));
                try {
                    threshold = Double.parseDouble(imgPart.substring(imgPart.indexOf(":THR:") + 5));
                } catch (NumberFormatException ignored) {}
            }
            fixed.setImageTemplate(base64);
            fixed.setImageThreshold(threshold);
        } else if (solution.contains("备选定位:")) {
            String newTarget = solution.substring(solution.indexOf(":") + 1).trim();
            fixed.setTarget(newTarget);
        } else {
            fixed.setTarget(original.getTarget());
        }

        fixed.setValue(original.getValue());
        fixed.setWaitTime(original.getWaitTime());
        if (fixed.getImageTemplate() == null) {
            fixed.setImageTemplate(original.getImageTemplate());
        }
        if (fixed.getImageThreshold() == null) {
            fixed.setImageThreshold(original.getImageThreshold());
        }
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