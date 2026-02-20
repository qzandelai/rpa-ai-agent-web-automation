package com.rpaai.service.impl;

import com.rpaai.entity.AutomationTask;
import com.rpaai.repository.AutomationTaskRepository;
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

    @Override
    @Transactional
    public AutomationTask parseNaturalLanguageTask(String naturalLanguage) {
        log.info("🚀 开始解析任务: {}", naturalLanguage);

        // 使用AI解析
        AutomationTask task = aiParsingService.parseWithAI(naturalLanguage);

        // ✅ 新增：保存前检查configJson长度
        if (task.getConfigJson() != null && task.getConfigJson().length() > 16777215) {
            // LONGTEXT最大约4GB，但超过16MB记录警告
            log.error("❌ 生成的配置JSON过大 ({} 字符)，无法存储", task.getConfigJson().length());
            throw new RuntimeException("任务配置过于复杂，请简化任务描述");
        }

        // 保存解析结果到数据库
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
            // 更新操作，确保时间戳更新
            task.setUpdateTime(java.time.LocalDateTime.now());
        }
        return taskRepository.save(task);
    }

    @Override
    @Transactional(readOnly = true)
    public AutomationTask getTaskById(Long id) {
        return taskRepository.findById(id).orElse(null);
    }
}