package com.rpaai.controller;

import com.rpaai.entity.Credentials;
import com.rpaai.service.CredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 凭据管理接口
 * 位置：src/main/java/com/rpaai/controller/CredentialsController.java
 * 提供凭据的增删改查接口
 */
@Slf4j
@RestController
@RequestMapping("/api/credentials")
@CrossOrigin(origins = "*")
public class CredentialsController {

    @Autowired
    private CredentialsService credentialsService;

    /**
     * 保存凭据（新建或更新）
     * POST /api/credentials/save
     */
    @PostMapping("/save")
    public ResponseEntity<Credentials> save(@RequestBody Credentials credentials) {
        log.info("💾 保存凭据: {}", credentials.getCredentialName());
        Credentials saved = credentialsService.saveCredentials(credentials);
        // 返回时脱敏密码
        saved.setPassword("***");
        return ResponseEntity.ok(saved);
    }

    /**
     * 获取所有凭据列表（脱敏）
     * GET /api/credentials/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<Credentials>> list() {
        List<Credentials> list = credentialsService.getAllCredentials();
        return ResponseEntity.ok(list);
    }

    /**
     * 根据网站查找凭据
     * GET /api/credentials/find?website=github
     */
    @GetMapping("/find")
    public ResponseEntity<List<Credentials>> findByWebsite(@RequestParam String website) {
        List<Credentials> list = credentialsService.findByWebsite(website);
        return ResponseEntity.ok(list);
    }

    /**
     * 删除凭据
     * DELETE /api/credentials/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        credentialsService.deleteCredentials(id);
        return ResponseEntity.ok(Map.of("message", "凭据已删除"));
    }
}