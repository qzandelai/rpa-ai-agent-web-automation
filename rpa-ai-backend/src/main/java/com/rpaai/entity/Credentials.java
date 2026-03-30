package com.rpaai.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 凭据实体 - 存储网站账号密码
 * 位置：src/main/java/com/rpaai/entity/Credentials.java
 */
@Data
@Entity
@Table(name = "credentials")
public class Credentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 凭据名称，用于展示选择，如"GitHub个人账号"
     */
    @Column(name = "credential_name", nullable = false, length = 100)
    private String credentialName;

    /**
     * 关联网站，如"github.com"，用于快速筛选
     */
    @Column(name = "website", length = 100)
    private String website;

    /**
     * 用户名/账号
     */
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /**
     * 密码（生产环境建议加密）
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * 创建用户ID
     */
    @Column(name = "create_user")
    private Long createUser;

    /**
     * 创建时间
     */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}