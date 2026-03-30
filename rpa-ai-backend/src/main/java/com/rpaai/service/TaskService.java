package com.rpaai.service;

import com.rpaai.entity.AutomationTask;

/**
 * 任务服务接口 - 修改版
 * 位置：src/main/java/com/rpaai/service/TaskService.java
 */
public interface TaskService {

    /**
     * 🆕 修改：增加凭据ID参数
     */
    AutomationTask parseWithAI(String naturalLanguage, Long credentialsId);

    AutomationTask saveTask(AutomationTask task);

    AutomationTask getTaskById(Long id);

    String submitTaskToScheduler(Long taskId, String userId, TaskPriority priority);

    String executeImmediately(Long taskId, String userId);
}