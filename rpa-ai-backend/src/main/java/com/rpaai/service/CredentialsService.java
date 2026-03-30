package com.rpaai.service;

import com.rpaai.entity.Credentials;
import com.rpaai.repository.CredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 凭据管理服务
 * 位置：src/main/java/com/rpaai/service/CredentialsService.java
 */
@Slf4j
@Service
public class CredentialsService {

    @Autowired
    private CredentialsRepository credentialsRepository;

    /**
     * 保存凭据
     */
    @Transactional
    public Credentials saveCredentials(Credentials credentials) {
        log.info("💾 保存凭据: {}", credentials.getCredentialName());
        return credentialsRepository.save(credentials);
    }

    /**
     * 根据ID获取凭据（完整信息，包含密码）
     */
    public Credentials getById(Long id) {
        return credentialsRepository.findById(id).orElse(null);
    }

    /**
     * 获取所有凭据列表（脱敏，用于展示）
     */
    public List<Credentials> getAllCredentials() {
        List<Credentials> list = credentialsRepository.findAll();
        // 脱敏处理：密码显示为***
        list.forEach(c -> c.setPassword("***"));
        return list;
    }

    /**
     * 根据网站查找凭据（脱敏）
     */
    public List<Credentials> findByWebsite(String website) {
        List<Credentials> list = credentialsRepository.findByWebsiteContaining(website);
        list.forEach(c -> c.setPassword("***"));
        return list;
    }

    /**
     * 删除凭据
     */
    @Transactional
    public void deleteCredentials(Long id) {
        credentialsRepository.deleteById(id);
        log.info("🗑️ 删除凭据: {}", id);
    }
}