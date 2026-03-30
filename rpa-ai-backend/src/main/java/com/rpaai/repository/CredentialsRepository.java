package com.rpaai.repository;

import com.rpaai.entity.Credentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 凭据数据访问层
 * 位置：src/main/java/com/rpaai/repository/CredentialsRepository.java
 */
@Repository
public interface CredentialsRepository extends JpaRepository<Credentials, Long> {

    /**
     * 根据网站域名查找凭据
     */
    List<Credentials> findByWebsiteContaining(String website);

    /**
     * 根据创建用户查找
     */
    List<Credentials> findByCreateUser(Long createUser);

    /**
     * 根据凭据名称模糊查询
     */
    List<Credentials> findByCredentialNameContaining(String name);
}