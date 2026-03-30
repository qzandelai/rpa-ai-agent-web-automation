package com.rpaai.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 自动化任务实体 - 修改版
 * 位置：src/main/java/com/rpaai/entity/AutomationTask.java
 */
@Data
@Entity
@Table(name = "automation_task")
public class AutomationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", length = 20)
    private String status = "DRAFT";

    @Column(name = "create_user")
    private Long createUser;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @Lob
    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;

    // ==================== 新增字段 ====================

    /**
     * 关联的凭据ID
     */
    @Column(name = "credentials_id")
    private Long credentialsId;

    /**
     * 是否需要凭据 Y/N
     */
    @Column(name = "need_credentials", length = 1)
    private String needCredentials = "N";

    // ==================== 新增字段结束 ====================

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}