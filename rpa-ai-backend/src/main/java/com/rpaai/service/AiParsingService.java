package com.rpaai.service;

import com.rpaai.core.ai.AiPromptTemplate;
import com.rpaai.entity.RpaStep;
import com.rpaai.entity.AutomationTask;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AiParsingService {

    @Autowired
    private ChatLanguageModel chatModel;

    public AutomationTask parseWithAI(String naturalLanguage) {
        log.info("🤖 开始AI解析任务: {}", naturalLanguage);

        // 构建Prompt
        String prompt = AiPromptTemplate.buildTaskPrompt(naturalLanguage);
        log.debug("AI Prompt:\n{}", prompt);

        // 调用AI
        long startTime = System.currentTimeMillis();
        String aiResponse = chatModel.generate(prompt);
        long duration = System.currentTimeMillis() - startTime;

        log.info("✅ AI响应耗时: {}ms", duration);
        log.debug("AI原始响应长度: {} 字符", aiResponse.length());

        // 解析AI返回的JSON
        try {
            // 提取JSON部分（如果AI返回了额外文本）
            String jsonStr = extractJson(aiResponse);

            // ✅ 新增：检查JSON长度，如果超过预警值记录日志
            if (jsonStr.length() > 10000) {
                log.warn("⚠️ 生成的JSON配置较长 ({} 字符)，可能影响存储性能", jsonStr.length());
            }

            // 解析为步骤列表（验证JSON有效性）
            List<RpaStep> steps = parseStepsFromJson(jsonStr);
            log.info("🎉 AI解析成功，生成 {} 个步骤", steps.size());

            // 构建任务对象
            AutomationTask task = new AutomationTask();
            task.setTaskName("AI生成任务_" + System.currentTimeMillis());
            task.setDescription("AI解析自: " + naturalLanguage.substring(0, Math.min(100, naturalLanguage.length())));
            task.setStatus("AI_PARSED");
            task.setConfigJson(jsonStr); // 存储完整步骤JSON

            return task;

        } catch (Exception e) {
            log.error("❌ AI解析失败: {}", e.getMessage(), e);
            // AI解析失败时，降级到简单规则解析
            return fallbackParse(naturalLanguage);
        }
    }

    private String extractJson(String aiResponse) {
        // 如果AI返回了markdown代码块，提取其中的JSON
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
        // 使用FastJson2解析
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

        // 简单规则：关键词匹配，生成精简JSON避免过长
        String configJson = buildFallbackConfig(naturalLanguage);
        task.setConfigJson(configJson);

        return task;
    }

    // ✅ 新增：构建精简的降级配置
    private String buildFallbackConfig(String naturalLanguage) {
        StringBuilder steps = new StringBuilder();
        steps.append("{\"steps\":[");

        int stepId = 1;
        // 打开网页
        if (naturalLanguage.contains("百度")) {
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://www.baidu.com\",\"description\":\"打开百度\"}", stepId++));
        } else if (naturalLanguage.contains("登录") || naturalLanguage.contains("访问")) {
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://www.example.com\",\"description\":\"打开目标网站\"}", stepId++));
        }

        // 输入操作
        if (naturalLanguage.contains("搜索") || naturalLanguage.contains("输入")) {
            if (stepId > 1) steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"input\",\"target\":\"input[type=text],#kw,#search\",\"value\":\"%s\",\"description\":\"输入搜索内容\"}",
                    stepId++, "搜索内容"));
        }

        // 点击操作
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