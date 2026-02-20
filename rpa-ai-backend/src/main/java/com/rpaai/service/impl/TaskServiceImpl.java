package com.rpaai.service.impl;

import com.rpaai.entity.AutomationTask;
import com.rpaai.repository.AutomationTaskRepository;
import com.rpaai.service.RpaTaskScheduler;
import com.rpaai.service.TaskPriority;
import com.rpaai.service.TaskService;
import com.rpaai.service.AiParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private AutomationTaskRepository taskRepository;

    @Autowired
    private AiParsingService aiParsingService;

    @Autowired
    private RpaTaskScheduler rpaTaskScheduler;

    @Override
    @Transactional
    public AutomationTask parseNaturalLanguageTask(String naturalLanguage) {
        log.info("🚀 开始解析任务: {}", naturalLanguage);

        AutomationTask task = aiParsingService.parseWithAI(naturalLanguage);

        if (task.getConfigJson() != null && task.getConfigJson().length() > 16777215) {
            log.error("❌ 生成的配置JSON过大 ({} 字符)，无法存储", task.getConfigJson().length());
            throw new RuntimeException("任务配置过于复杂，请简化任务描述");
        }

        try {
            AutomationTask saved = taskRepository.save(task);
            log.info("✅ 任务保存成功，ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            log.error("❌ 保存任务失败: {}", e.getMessage());
            throw new RuntimeException("保存任务失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public AutomationTask saveTask(AutomationTask task) {
        if (task.getId() != null) {
            task.setUpdateTime(java.time.LocalDateTime.now());
        }
        return taskRepository.save(task);
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationTask getTaskById(Long id) {
        return taskRepository.findById(id).orElse(null);
    }

    // 🆕 新增：提交任务到调度队列执行
    @Override
    public String submitTaskToScheduler(Long taskId, String userId, TaskPriority priority) {
        log.info("📥 提交任务到调度器: taskId={}, userId={}", taskId, userId);

        AutomationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        if (task.getConfigJson() == null || task.getConfigJson().isEmpty()) {
            throw new RuntimeException("任务配置为空，请先解析任务");
        }

        return rpaTaskScheduler.submitTask(task, userId, priority);
    }

    // 🆕 新增：立即执行任务（不进入队列等待）
    @Override
    public String executeImmediately(Long taskId, String userId) {
        log.info("⚡ 立即执行任务: taskId={}", taskId);
        return submitTaskToScheduler(taskId, userId, TaskPriority.HIGH);
    }
}