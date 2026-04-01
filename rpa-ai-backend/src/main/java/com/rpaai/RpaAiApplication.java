package com.rpaai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync          // ✅ 添加
@EnableScheduling     // ✅ 添加
@EnableNeo4jRepositories(basePackages = "com.rpaai.repository.neo4j")  // ✅ 确保这行存在
public class RpaAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RpaAiApplication.class, args);
    }
}