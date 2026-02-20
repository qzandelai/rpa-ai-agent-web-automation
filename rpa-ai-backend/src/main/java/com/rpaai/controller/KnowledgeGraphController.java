package com.rpaai.controller;

import com.rpaai.service.KnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}