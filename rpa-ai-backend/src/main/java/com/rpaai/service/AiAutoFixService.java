package com.rpaai.service;

import com.rpaai.entity.RpaStep;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AiAutoFixService {

    @Autowired
    private ChatLanguageModel chatModel;

    /**
     * 使用 LLM 分析页面上下文，实时生成修复方案
     */
    public Optional<RpaStep> fixStep(RpaStep failedStep, String errorMessage,
                                     String currentUrl, String pageContext) {
        try {
            String prompt = buildPrompt(failedStep, errorMessage, currentUrl, pageContext);
            log.info("🤖 调用 LLM 进行运行时修复诊断，步骤: {}", failedStep.getStepId());

            long start = System.currentTimeMillis();
            String aiResponse = chatModel.generate(prompt);
            long duration = System.currentTimeMillis() - start;
            log.info("✅ LLM 修复响应耗时: {}ms", duration);

            return parseFixResponse(failedStep, aiResponse);
        } catch (Exception e) {
            log.error("❌ LLM 自动修复失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String buildPrompt(RpaStep step, String error, String url, String pageHtml) {
        return String.format(
                "你是一个网页自动化修复专家。当前步骤执行失败了，请分析原因并给出修复方案。\n\n" +
                "【失败信息】\n" +
                "- 步骤编号: %d\n" +
                "- 动作: %s\n" +
                "- 目标选择器: %s\n" +
                "- 输入值: %s\n" +
                "- 错误信息: %s\n" +
                "- 当前URL: %s\n\n" +
                "【页面HTML片段】\n%s\n\n" +
                "请返回一个严格的JSON对象，只包含以下字段，不要有任何额外说明或markdown代码块标记：\n" +
                "{\n" +
                "  \"action\": \"click|input|wait|submit|scroll|extract|fail\",\n" +
                "  \"target\": \"新的CSS选择器，多个用逗号分隔\",\n" +
                "  \"value\": \"如果需要输入的值\",\n" +
                "  \"reason\": \"简要说明为什么这样修复\"\n" +
                "}\n\n" +
                "约束：\n" +
                "1. 不要改变任务意图，只修正定位方式或动作\n" +
                "2. 优先使用 id、name、aria-label、placeholder 等稳定属性\n" +
                "3. 如果页面已经处于目标状态（比如已登录、已搜索），返回 action=\"wait\"，target为空\n" +
                "4. 如果确实无法修复，返回 action=\"fail\"\n" +
                "5. 对于搜索框输入，如果找不到精确选择器，可以尝试 input[name='q']、input[type='text'] 等通用选择器\n" +
                "6. 对于提交搜索，优先使用 submit 动作或点击搜索按钮\n",
                step.getStepId(),
                step.getAction() != null ? step.getAction() : "",
                step.getTarget() != null ? step.getTarget() : "",
                step.getValue() != null ? step.getValue() : "",
                error != null ? error : "",
                url != null ? url : "",
                pageHtml != null ? pageHtml : ""
        );
    }

    private Optional<RpaStep> parseFixResponse(RpaStep original, String aiResponse) {
        try {
            String jsonStr = extractJson(aiResponse);
            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(jsonStr);

            String action = json.getString("action");
            if ("fail".equals(action)) {
                log.warn("🤖 LLM 判断无法修复: {}", json.getString("reason"));
                return Optional.empty();
            }

            RpaStep fixed = new RpaStep();
            fixed.setStepId(original.getStepId());
            fixed.setAction(action != null ? action : original.getAction());
            fixed.setTarget(json.getString("target") != null ? json.getString("target") : original.getTarget());
            fixed.setValue(json.getString("value") != null ? json.getString("value") : original.getValue());
            fixed.setDescription(original.getDescription() + " [LLM修复]");
            fixed.setRetryCount(1);
            fixed.setWaitTime(original.getWaitTime());
            fixed.setImageTemplate(original.getImageTemplate());
            fixed.setImageThreshold(original.getImageThreshold());

            log.info("💡 LLM 修复方案: action={}, target={}, reason={}",
                    fixed.getAction(), fixed.getTarget(), json.getString("reason"));
            return Optional.of(fixed);
        } catch (Exception e) {
            log.error("❌ 解析 LLM 修复响应失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractJson(String text) {
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
