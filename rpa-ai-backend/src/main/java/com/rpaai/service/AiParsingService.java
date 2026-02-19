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
        log.debug("AI原始响应:\n{}", aiResponse);

        // 解析AI返回的JSON
        try {
            // 提取JSON部分（如果AI返回了额外文本）
            String jsonStr = extractJson(aiResponse);

            // 解析为步骤列表
            List<RpaStep> steps = parseStepsFromJson(jsonStr);

            // 构建任务对象
            AutomationTask task = new AutomationTask();
            task.setTaskName("AI生成任务_" + System.currentTimeMillis());
            task.setDescription("AI解析自: " + naturalLanguage);
            task.setStatus("AI_PARSED");
            task.setConfigJson(jsonStr); // 存储完整步骤JSON

            log.info("🎉 AI解析成功，生成 {} 个步骤", steps.size());
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
        log.warn("使用降级解析策略");
        AutomationTask task = new AutomationTask();
        task.setTaskName("降级解析任务");
        task.setDescription("AI解析失败，使用简单规则: " + naturalLanguage);
        task.setStatus("FALLBACK_PARSED");

        // 简单规则：关键词匹配
        String configJson = "{\"steps\":[]}";
        if (naturalLanguage.contains("登录")) {
            configJson = "{\"steps\":[{\"stepId\":1,\"action\":\"open_url\",\"target\":\"https://www.example.com\",\"description\":\"打开登录页面\"},{\"stepId\":2,\"action\":\"input\",\"target\":\"#username\",\"description\":\"输入用户名\"},{\"stepId\":3,\"action\":\"input\",\"target\":\"#password\",\"description\":\"输入密码\"},{\"stepId\":4,\"action\":\"click\",\"target\":\"#login\",\"description\":\"点击登录\"}]}";
        } else if (naturalLanguage.contains("搜索")) {
            configJson = "{\"steps\":[{\"stepId\":1,\"action\":\"open_url\",\"target\":\"https://www.baidu.com\",\"description\":\"打开百度\"},{\"stepId\":2,\"action\":\"input\",\"target\":\"#kw\",\"description\":\"输入搜索词\"},{\"stepId\":3,\"action\":\"click\",\"target\":\"#su\",\"description\":\"点击搜索\"}]}";
        }

        task.setConfigJson(configJson);
        return task;
    }
}