// TaskService.java（接口也需要更新）
package com.rpaai.service;

import com.rpaai.entity.AutomationTask;

public interface TaskService {
    AutomationTask parseNaturalLanguageTask(String naturalLanguage);
    AutomationTask saveTask(AutomationTask task);
    AutomationTask getTaskById(Long id);

    // 🆕 新增接口方法
    String submitTaskToScheduler(Long taskId, String userId, TaskPriority priority);
    String executeImmediately(Long taskId, String userId);
}