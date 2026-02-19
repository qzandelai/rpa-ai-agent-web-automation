package com.rpaai.service.impl;

import com.rpaai.entity.AutomationTask;
import com.rpaai.repository.AutomationTaskRepository;
import com.rpaai.service.TaskService;
import com.rpaai.service.AiParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    @Autowired
    private AutomationTaskRepository taskRepository;

    @Autowired
    private AiParsingService aiParsingService;

    @Override
    public AutomationTask parseNaturalLanguageTask(String naturalLanguage) {
        log.info("🚀 开始解析任务: {}", naturalLanguage);

        // 使用AI解析
        AutomationTask task = aiParsingService.parseWithAI(naturalLanguage);

        // 保存解析结果到数据库
        return taskRepository.save(task);
    }

    @Override
    public AutomationTask saveTask(AutomationTask task) {
        return taskRepository.save(task);
    }

    @Override
    public AutomationTask getTaskById(Long id) {
        return taskRepository.findById(id).orElse(null);
    }
}