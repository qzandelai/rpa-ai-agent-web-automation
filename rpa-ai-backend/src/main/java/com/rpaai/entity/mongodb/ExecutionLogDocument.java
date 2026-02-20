package com.rpaai.entity.mongodb;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "execution_logs")
public class ExecutionLogDocument {

    @Id
    private String id;

    private Long taskId;                    // 关联的任务ID
    private String taskName;                // 任务名称
    private String naturalLanguage;         // 原始自然语言描述

    private boolean success;                // 是否成功
    private int totalSteps;                 // 总步骤数
    private int completedSteps;             // 完成步骤数
    private String errorMessage;            // 错误信息

    private List<StepLog> stepLogs;         // 每步执行详情
    private Map<String, Object> metadata;   // 元数据（浏览器版本、执行时长等）

    private LocalDateTime startTime;        // 开始时间
    private LocalDateTime endTime;          // 结束时间
    private long durationMs;                // 总耗时（毫秒）

    private String screenshotPath;          // 截图路径

    @Data
    public static class StepLog {
        private Integer stepId;
        private String action;
        private String target;
        private boolean success;
        private String message;
        private String errorMessage;
        private long executionTimeMs;
        private LocalDateTime executeTime;
    }
}