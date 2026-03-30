package com.rpaai.service.impl;

import com.rpaai.entity.AutomationTask;
import com.rpaai.repository.AutomationTaskRepository;
import com.rpaai.service.*;
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

    /**
     * 🆕 修改：增加凭据ID参数
     */
    @Override
    @Transactional
    public AutomationTask parseWithAI(String naturalLanguage, Long credentialsId) {
        log.info("🚀 开始解析任务: {}, 凭据ID: {}", naturalLanguage, credentialsId);

        // 调用带凭据的解析方法
        AutomationTask task = aiParsingService.parseWithAI(naturalLanguage, credentialsId);

        // 保存到数据库
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

    @Override
    public String submitTaskToScheduler(Long taskId, String userId, TaskPriority priority) {
        AutomationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
        return rpaTaskScheduler.submitTask(task, userId, priority);
    }

    @Override
    public String executeImmediately(Long taskId, String userId) {
        return submitTaskToScheduler(taskId, userId, TaskPriority.HIGH);
    }
}