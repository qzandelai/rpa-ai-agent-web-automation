package com.rpaai.entity.mongodb;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据提取记录 - 存储RPA采集的结构化数据
 */
@Data
@Document(collection = "extracted_data")
public class DataExtractRecord {

    @Id
    private String id;

    private Long taskId;                    // 关联任务ID
    private String taskName;                // 任务名称
    private String executionId;             // 执行ID
    private String sourceUrl;               // 数据来源URL

    private String extractType;             // 提取类型：single/list/table
    private List<String> headers;           // 表头/字段名
    private List<Map<String, Object>> rows; // 数据行

    private String rawSelector;             // 使用的CSS选择器
    private LocalDateTime extractTime;      // 提取时间
    private String screenshotPath;          // 关联截图路径
}