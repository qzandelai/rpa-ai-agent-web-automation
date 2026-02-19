package com.rpaai.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    @Column(name = "description")
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
    @Column(name = "config_json")
    private String configJson;
}