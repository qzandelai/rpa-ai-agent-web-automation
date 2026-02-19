package com.rpaai.service;

import com.rpaai.entity.AutomationTask;

public interface TaskService {
    AutomationTask parseNaturalLanguageTask(String naturalLanguage);
    AutomationTask saveTask(AutomationTask task);
    AutomationTask getTaskById(Long id);
}