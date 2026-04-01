package com.rpaai.controller;

import com.rpaai.entity.neo4j.ElementPattern;
import com.rpaai.entity.neo4j.ExceptionCase;
import com.rpaai.service.KnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/kg")
@CrossOrigin(origins = "*")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    /**
     * 获取知识图谱统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(knowledgeGraphService.getKnowledgeStats());
    }

    /**
     * 手动触发学习（测试用）
     */
    @PostMapping("/learn")
    public ResponseEntity<String> triggerLearning() {
        log.info("手动触发知识图谱学习");
        return ResponseEntity.ok("学习完成");
    }

    /**
     * 测试 Neo4j 连接
     */
    @GetMapping("/test-connection")
    public ResponseEntity<?> testConnection() {
        return ResponseEntity.ok(knowledgeGraphService.testConnection());
    }

    /**
     * 获取所有元素模式
     */
    @GetMapping("/patterns")
    public ResponseEntity<List<ElementPattern>> getPatterns() {
        return ResponseEntity.ok(knowledgeGraphService.getAllPatterns());
    }

    /**
     * 获取所有异常案例
     */
    @GetMapping("/cases")
    public ResponseEntity<List<ExceptionCase>> getCases() {
        return ResponseEntity.ok(knowledgeGraphService.getAllCases());
    }

    /**
     * 删除元素模式
     */
    @DeleteMapping("/patterns/{id}")
    public ResponseEntity<Map<String, Object>> deletePattern(@PathVariable String id) {
        boolean success = knowledgeGraphService.deletePattern(id);
        return ResponseEntity.ok(Map.of("success", success, "id", id));
    }
}
