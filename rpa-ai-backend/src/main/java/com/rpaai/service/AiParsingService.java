package com.rpaai.service;

import com.rpaai.core.ai.AiPromptTemplate;
import com.rpaai.entity.RpaStep;
import com.rpaai.entity.AutomationTask;
import com.rpaai.entity.StepResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiParsingService {

    @Autowired
    private ChatLanguageModel chatModel;

    public AutomationTask parseWithAI(String naturalLanguage) {
        log.info("🤖 开始AI解析任务: {}", naturalLanguage);

        String prompt = AiPromptTemplate.buildTaskPrompt(naturalLanguage);
        log.debug("AI Prompt:\n{}", prompt);

        long startTime = System.currentTimeMillis();
        String aiResponse = chatModel.generate(prompt);
        long duration = System.currentTimeMillis() - startTime;

        log.info("✅ AI响应耗时: {}ms", duration);
        log.debug("AI原始响应长度: {} 字符", aiResponse.length());

        try {
            String jsonStr = extractJson(aiResponse);

            if (jsonStr.length() > 10000) {
                log.warn("⚠️ 生成的JSON配置较长 ({} 字符)，可能影响存储性能", jsonStr.length());
            }

            List<RpaStep> steps = parseStepsFromJson(jsonStr);
            log.info("🎉 AI解析成功，生成 {} 个步骤", steps.size());

            AutomationTask task = new AutomationTask();
            task.setTaskName("AI生成任务_" + System.currentTimeMillis());
            task.setDescription("AI解析自: " + naturalLanguage.substring(0, Math.min(100, naturalLanguage.length())));
            task.setStatus("AI_PARSED");
            task.setConfigJson(jsonStr);

            return task;

        } catch (Exception e) {
            log.error("❌ AI解析失败: {}", e.getMessage(), e);
            return fallbackParse(naturalLanguage);
        }
    }

    public List<RpaStep> replanSteps(String originalDescription,
                                     List<StepResult> completedSteps,
                                     StepResult failure,
                                     String currentUrl) {
        log.info("🧠 AI开始动态重规划，已完成{}步，当前URL: {}", completedSteps.size(), currentUrl);

        String completedActions = completedSteps.stream()
                .map(s -> "步骤" + s.getStepId() + ":" + s.getMessage())
                .collect(Collectors.joining("\n"));

        String replanPrompt = String.format("""
            你是RPA流程修复专家。原任务执行中断，需要根据当前状态重新规划剩余步骤。
            
            原始任务：%s
            
            已完成的步骤：
            %s
            
            失败的步骤：步骤%d
            失败原因：%s
            
            当前页面URL：%s
            
            请分析：
            1. 失败是否因为页面结构变化？
            2. 是否需要跳过某些步骤？
            3. 是否需要采用替代定位策略？
            
            输出要求：
            - 只输出剩余需要执行的步骤JSON数组
            - 步骤编号从%d开始继续
            - 使用更鲁棒的选择器（多属性组合）
            - 在关键操作前增加等待步骤
            
            输出格式：
            {
              "steps": [
                {
                  "stepId": %d,
                  "action": "wait",
                  "waitTime": 2,
                  "description": "等待页面稳定"
                },
                ...
              ]
            }
            """,
                originalDescription,
                completedActions,
                failure.getStepId(),
                failure.getError(),
                currentUrl,
                failure.getStepId() + 1,
                failure.getStepId() + 1
        );

        try {
            String aiResponse = chatModel.generate(replanPrompt);
            String jsonStr = extractJson(aiResponse);

            com.alibaba.fastjson2.JSONObject jsonObject =
                    com.alibaba.fastjson2.JSON.parseObject(jsonStr);
            List<RpaStep> newSteps = jsonObject.getList("steps", RpaStep.class);

            log.info("✅ AI重规划成功，生成 {} 个新步骤", newSteps.size());
            return newSteps;

        } catch (Exception e) {
            log.error("❌ AI重规划失败: {}", e.getMessage());
            return null;
        }
    }

    public List<RpaStep> parseSteps(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            throw new RuntimeException("任务配置为空");
        }
        try {
            com.alibaba.fastjson2.JSONObject json =
                    com.alibaba.fastjson2.JSON.parseObject(configJson);
            return json.getList("steps", RpaStep.class);
        } catch (Exception e) {
            log.error("解析步骤失败: {}", configJson, e);
            throw new RuntimeException("解析任务步骤失败: " + e.getMessage());
        }
    }

    private String extractJson(String aiResponse) {
        if (aiResponse.contains("```json")) {
            return aiResponse.substring(
                    aiResponse.indexOf("```json") + 7,
                    aiResponse.lastIndexOf("```")
            ).trim();
        }
        if (aiResponse.contains("```")) {
            return aiResponse.substring(
                    aiResponse.indexOf("```") + 3,
                    aiResponse.lastIndexOf("```")
            ).trim();
        }
        return aiResponse.trim();
    }

    private List<RpaStep> parseStepsFromJson(String jsonStr) throws Exception {
        com.alibaba.fastjson2.JSONObject jsonObject =
                com.alibaba.fastjson2.JSON.parseObject(jsonStr);
        return jsonObject.getList("steps", RpaStep.class);
    }

    private AutomationTask fallbackParse(String naturalLanguage) {
        log.warn("⚠️ 使用降级解析策略");
        AutomationTask task = new AutomationTask();
        task.setTaskName("降级解析任务_" + System.currentTimeMillis());
        task.setDescription("AI解析失败，使用简单规则: " + naturalLanguage.substring(0, Math.min(50, naturalLanguage.length())));
        task.setStatus("FALLBACK_PARSED");

        String configJson = buildFallbackConfig(naturalLanguage);
        task.setConfigJson(configJson);

        return task;
    }

    private String buildFallbackConfig(String naturalLanguage) {
        StringBuilder steps = new StringBuilder();
        steps.append("{\"steps\":[");

        int stepId = 1;
        if (naturalLanguage.contains("百度")) {
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://www.baidu.com\",\"description\":\"打开百度\"}", stepId++));
        } else if (naturalLanguage.contains("登录") || naturalLanguage.contains("访问")) {
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://www.example.com\",\"description\":\"打开目标网站\"}", stepId++));
        }

        if (naturalLanguage.contains("搜索") || naturalLanguage.contains("输入")) {
            if (stepId > 1) steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"input\",\"target\":\"input[type=text],#kw,#search\",\"value\":\"%s\",\"description\":\"输入搜索内容\"}",
                    stepId++, "搜索内容"));
        }

        if (naturalLanguage.contains("点击") || naturalLanguage.contains("搜索") || naturalLanguage.contains("登录")) {
            if (stepId > 1) steps.append(",");
            String target = naturalLanguage.contains("登录") ? "#login,.login-btn" : "#su,.search-btn,button[type=submit]";
            steps.append(String.format("{\"stepId\":%d,\"action\":\"click\",\"target\":\"%s\",\"description\":\"执行操作\"}",
                    stepId++, target));
        }

        steps.append("]}");
        return steps.toString();
    }
}