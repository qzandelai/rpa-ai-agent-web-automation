package com.rpaai.service;

import com.rpaai.core.ai.AiPromptTemplate;
import com.rpaai.entity.AutomationTask;
import com.rpaai.entity.Credentials;
import com.rpaai.entity.RpaStep;
import com.rpaai.entity.StepResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiParsingService {

    @Autowired
    private ChatLanguageModel chatModel;

    @Autowired
    private CredentialsService credentialsService;

    /**
     * 带凭据的任务解析
     */
    public AutomationTask parseWithAI(String naturalLanguage, Long credentialsId) {
        log.info("🤖 开始AI解析任务: {}, 凭据ID: {}", naturalLanguage, credentialsId);

        String prompt = AiPromptTemplate.buildTaskPrompt(naturalLanguage);

        long startTime = System.currentTimeMillis();
        String aiResponse = chatModel.generate(prompt);
        long duration = System.currentTimeMillis() - startTime;

        log.info("✅ AI响应耗时: {}ms", duration);

        try {
            String jsonStr = extractJson(aiResponse);
            List<RpaStep> steps = parseStepsFromJson(jsonStr);

            // 优化登录步骤
            steps = optimizeLoginSteps(steps, naturalLanguage);

            // 如果有凭据，注入凭据占位符
            if (credentialsId != null) {
                steps = injectCredentialsPlaceholder(steps, credentialsId);
            }

            // 构建任务对象
            AutomationTask task = new AutomationTask();
            task.setTaskName("AI生成任务_" + System.currentTimeMillis());
            task.setDescription("AI解析自: " + naturalLanguage.substring(0, Math.min(100, naturalLanguage.length())));
            task.setStatus("AI_PARSED");
            task.setCredentialsId(credentialsId);
            task.setNeedCredentials(credentialsId != null ? "Y" : "N");

            // 关键：使用包含图像模板的方法重构JSON
            String optimizedJson = rebuildConfigJson(steps);
            task.setConfigJson(optimizedJson);

            return task;

        } catch (Exception e) {
            log.error("❌ AI解析失败: {}", e.getMessage(), e);
            return fallbackParse(naturalLanguage, credentialsId);
        }
    }

    /**
     * 在步骤中注入凭据占位符
     */
    private List<RpaStep> injectCredentialsPlaceholder(List<RpaStep> steps, Long credentialsId) {
        Credentials credentials = credentialsService.getById(credentialsId);
        if (credentials == null) {
            log.warn("⚠️ 凭据不存在: {}", credentialsId);
            return steps;
        }

        log.info("🔐 注入凭据占位符: {} (用户: {})",
                credentials.getCredentialName(),
                maskString(credentials.getUsername()));

        for (RpaStep step : steps) {
            if ("input".equals(step.getAction())) {
                String target = step.getTarget() != null ? step.getTarget().toLowerCase() : "";
                String desc = step.getDescription() != null ? step.getDescription().toLowerCase() : "";

                // 判断是用户名还是密码字段
                if (isUsernameField(target, desc, "")) {
                    step.setValue("{{CREDENTIALS_USERNAME:" + credentialsId + "}}");
                    step.setDescription(step.getDescription() + " [使用保存的账号]");
                    log.info("  → 账号字段: {} → 使用凭据占位符", step.getTarget());

                } else if (isPasswordField(target, desc, "")) {
                    step.setValue("{{CREDENTIALS_PASSWORD:" + credentialsId + "}}");
                    step.setDescription(step.getDescription() + " [使用保存的密码]");
                    log.info("  → 密码字段: {} → 使用凭据占位符", step.getTarget());
                }
            }
        }

        return steps;
    }

    /**
     * 执行任务前替换凭据占位符为真实值
     */
    public List<RpaStep> resolveCredentials(List<RpaStep> steps) {
        List<RpaStep> resolvedSteps = new ArrayList<>();

        for (RpaStep step : steps) {
            RpaStep resolvedStep = copyStep(step);
            String value = step.getValue();

            // 解析凭据占位符
            if (value != null && value.startsWith("{{CREDENTIALS_")) {
                Long credentialsId = extractCredentialsId(value);
                String fieldType = extractCredentialsFieldType(value);

                if (credentialsId != null) {
                    Credentials credentials = credentialsService.getById(credentialsId);
                    if (credentials != null) {
                        if ("USERNAME".equals(fieldType)) {
                            resolvedStep.setValue(credentials.getUsername());
                            log.info("🔐 替换账号: {} → ******", maskString(credentials.getUsername()));
                        } else if ("PASSWORD".equals(fieldType)) {
                            resolvedStep.setValue(credentials.getPassword());
                            log.info("🔐 替换密码: ***");
                        }
                    } else {
                        log.error("❌ 凭据不存在: {}", credentialsId);
                        throw new RuntimeException("凭据不存在: " + credentialsId);
                    }
                }
            } else {
                resolvedStep.setValue(value);
            }

            resolvedSteps.add(resolvedStep);
        }

        return resolvedSteps;
    }

    /**
     * 从占位符提取凭据ID
     */
    private Long extractCredentialsId(String placeholder) {
        try {
            String idStr = placeholder.replaceAll(".*:(\\d+)}}", "$1");
            return Long.parseLong(idStr);
        } catch (Exception e) {
            log.error("解析凭据ID失败: {}", placeholder);
            return null;
        }
    }

    /**
     * 从占位符提取字段类型
     */
    private String extractCredentialsFieldType(String placeholder) {
        if (placeholder.contains("USERNAME")) return "USERNAME";
        if (placeholder.contains("PASSWORD")) return "PASSWORD";
        return null;
    }

    /**
     * 复制步骤对象
     */
    private RpaStep copyStep(RpaStep source) {
        RpaStep copy = new RpaStep();
        copy.setStepId(source.getStepId());
        copy.setAction(source.getAction());
        copy.setTarget(source.getTarget());
        copy.setValue(source.getValue());
        copy.setWaitTime(source.getWaitTime());
        copy.setDescription(source.getDescription());
        copy.setFallbackTarget(source.getFallbackTarget());
        copy.setRequired(source.getRequired());
        copy.setRetryCount(source.getRetryCount());
        copy.setImageTemplate(source.getImageTemplate());  // 复制图像模板
        copy.setImageThreshold(source.getImageThreshold());
        return copy;
    }

    /**
     * 脱敏显示字符串
     */
    private String maskString(String str) {
        if (str == null || str.length() <= 4) return "****";
        return str.substring(0, 2) + "****" + str.substring(str.length() - 2);
    }

    /**
     * 优化登录步骤（检测"直接登录"意图）
     */
    private List<RpaStep> optimizeLoginSteps(List<RpaStep> steps, String originalInput) {
        if (steps == null || steps.isEmpty()) {
            return steps;
        }

        String lowerInput = originalInput.toLowerCase();

        // 检测"直接登录"意图
        boolean isDirectLogin = containsAny(lowerInput,
                "不用输", "直接登", "已保存", "记住密码", "保存了密码",
                "有密码", "跳过输入", "自动登", "一键登录", "免输入"
        );

        boolean hasExplicitCredentials = containsAny(lowerInput,
                "账号是", "用户名是", "密码是", "账号:", "密码:",
                "user:", "pass:", "登录名是"
        );

        if (!isDirectLogin || hasExplicitCredentials) {
            return steps;
        }

        log.info("🔍 检测到'直接登录'意图，优化步骤序列");

        List<RpaStep> optimized = new ArrayList<>();

        for (RpaStep step : steps) {
            String action = step.getAction();
            String target = step.getTarget() != null ? step.getTarget().toLowerCase() : "";
            String desc = step.getDescription() != null ? step.getDescription().toLowerCase() : "";
            String value = step.getValue() != null ? step.getValue().toLowerCase() : "";

            // 跳过账号输入步骤
            if ("input".equals(action) && isUsernameField(target, desc, value)) {
                log.info("⏭️ 跳过账号输入步骤: {}", step.getDescription());
                continue;
            }

            // 跳过密码输入步骤
            if ("input".equals(action) && isPasswordField(target, desc, value)) {
                log.info("⏭️ 跳过密码输入步骤: {}", step.getDescription());
                continue;
            }

            optimized.add(step);
        }

        // 重新编号
        for (int i = 0; i < optimized.size(); i++) {
            optimized.get(i).setStepId(i + 1);
        }

        log.info("✅ 步骤优化完成: {} 步 → {} 步", steps.size(), optimized.size());
        return optimized;
    }

    private boolean isUsernameField(String target, String description, String value) {
        String combined = (target + " " + description + " " + value).toLowerCase();
        String[] keywords = { "user", "username", "name", "account", "email", "mail", "login",
                "账号", "用户名", "账户", "邮箱", "邮件", "登录名", "帐号" };

        // 排除密码字段
        if (combined.contains("pass") || combined.contains("密码") || combined.contains("pwd")) {
            return false;
        }

        for (String k : keywords) {
            if (combined.contains(k)) return true;
        }
        return false;
    }

    private boolean isPasswordField(String target, String description, String value) {
        String combined = (target + " " + description + " " + value).toLowerCase();
        String[] keywords = { "pass", "password", "pwd", "密码", "口令", "密钥" };

        for (String k : keywords) {
            if (combined.contains(k)) return true;
        }
        return false;
    }

    private boolean containsAny(String input, String... keywords) {
        for (String k : keywords) {
            if (input.contains(k)) return true;
        }
        return false;
    }

    /**
     * 从AI响应中提取JSON
     */
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
        // 如果没有代码块标记，尝试直接解析花括号内容
        int start = aiResponse.indexOf('{');
        int end = aiResponse.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return aiResponse.substring(start, end + 1);
        }
        return aiResponse.trim();
    }

    /**
     * 从JSON解析步骤列表（支持图像模板字段）
     */
    private List<RpaStep> parseStepsFromJson(String jsonStr) throws Exception {
        com.alibaba.fastjson2.JSONObject jsonObject = com.alibaba.fastjson2.JSON.parseObject(jsonStr);
        List<RpaStep> steps = new ArrayList<>();

        com.alibaba.fastjson2.JSONArray stepsArray = jsonObject.getJSONArray("steps");
        if (stepsArray == null) {
            throw new RuntimeException("JSON中未找到steps数组");
        }

        for (int i = 0; i < stepsArray.size(); i++) {
            com.alibaba.fastjson2.JSONObject stepJson = stepsArray.getJSONObject(i);
            RpaStep step = new RpaStep();

            step.setStepId(stepJson.getInteger("stepId"));
            step.setAction(stepJson.getString("action"));
            step.setTarget(stepJson.getString("target"));
            step.setValue(stepJson.getString("value"));
            step.setWaitTime(stepJson.getInteger("waitTime"));
            step.setDescription(stepJson.getString("description"));
            step.setFallbackTarget(stepJson.getString("fallbackTarget"));

            // 解析图像模板字段（如果存在）
            if (stepJson.containsKey("imageTemplate")) {
                step.setImageTemplate(stepJson.getString("imageTemplate"));
            }
            if (stepJson.containsKey("imageThreshold")) {
                step.setImageThreshold(stepJson.getDouble("imageThreshold"));
            }

            steps.add(step);
        }

        return steps;
    }

    /**
     * 重构配置JSON（包含图像模板数据）
     */
    private String rebuildConfigJson(List<RpaStep> steps) {
        com.alibaba.fastjson2.JSONArray stepArray = new com.alibaba.fastjson2.JSONArray();

        for (RpaStep step : steps) {
            com.alibaba.fastjson2.JSONObject stepJson = new com.alibaba.fastjson2.JSONObject();
            stepJson.put("stepId", step.getStepId());
            stepJson.put("action", step.getAction());
            stepJson.put("target", step.getTarget());
            stepJson.put("value", step.getValue());
            stepJson.put("waitTime", step.getWaitTime());
            stepJson.put("description", step.getDescription());
            stepJson.put("fallbackTarget", step.getFallbackTarget());
            stepJson.put("required", step.getRequired());
            stepJson.put("retryCount", step.getRetryCount());

            // 关键：保存图像模板数据到数据库
            if (step.getImageTemplate() != null && !step.getImageTemplate().isEmpty()) {
                stepJson.put("imageTemplate", step.getImageTemplate());
                stepJson.put("imageThreshold", step.getImageThreshold() != null ?
                        step.getImageThreshold() : 0.8);
            }

            stepArray.add(stepJson);
        }

        com.alibaba.fastjson2.JSONObject config = new com.alibaba.fastjson2.JSONObject();
        config.put("steps", stepArray);
        return config.toJSONString();
    }

    /**
     * 降级解析策略
     */
    private AutomationTask fallbackParse(String naturalLanguage, Long credentialsId) {
        log.warn("⚠️ 使用降级解析策略");
        AutomationTask task = new AutomationTask();
        task.setTaskName("降级解析任务_" + System.currentTimeMillis());
        task.setDescription("AI解析失败: " + naturalLanguage.substring(0, Math.min(50, naturalLanguage.length())));
        task.setStatus("FALLBACK_PARSED");
        task.setCredentialsId(credentialsId);
        task.setNeedCredentials(credentialsId != null ? "Y" : "N");

        String configJson = buildFallbackConfig(naturalLanguage, credentialsId);
        task.setConfigJson(configJson);

        return task;
    }

    /**
     * 构建降级配置
     */
    private String buildFallbackConfig(String naturalLanguage, Long credentialsId) {
        StringBuilder steps = new StringBuilder();
        steps.append("{\"steps\":[");

        String lowerInput = naturalLanguage.toLowerCase();
        int stepId = 1;

        if (lowerInput.contains("github")) {
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://github.com/login\",\"description\":\"打开GitHub登录页\"}", stepId++));

            String usernameValue = credentialsId != null ?
                    "{{CREDENTIALS_USERNAME:" + credentialsId + "}}" : "username";
            String passwordValue = credentialsId != null ?
                    "{{CREDENTIALS_PASSWORD:" + credentialsId + "}}" : "password";

            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"input\",\"target\":\"#login_field\",\"value\":\"%s\",\"description\":\"输入用户名%s\"}",
                    stepId++, usernameValue, credentialsId != null ? "[凭据]" : ""));
            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"input\",\"target\":\"#password\",\"value\":\"%s\",\"description\":\"输入密码%s\"}",
                    stepId++, passwordValue, credentialsId != null ? "[凭据]" : ""));
            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"wait\",\"waitTime\":2,\"description\":\"等待输入完成\"}", stepId++));
            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"click\",\"target\":\"input[type=\\\"submit\\\"][name=\\\"commit\\\"]\",\"description\":\"点击登录按钮\"}", stepId++));
        } else if (lowerInput.contains("百度")) {
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://www.baidu.com\",\"description\":\"打开百度首页\"}", stepId++));
            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"wait\",\"waitTime\":2,\"description\":\"等待页面加载\"}", stepId++));
            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"input\",\"target\":\"#kw\",\"value\":\"%s\",\"description\":\"输入搜索关键词\"}",
                    stepId++, naturalLanguage.replaceAll("(?i)搜索|百度|查找", "").trim()));
            steps.append(",");
            steps.append(String.format("{\"stepId\":%d,\"action\":\"click\",\"target\":\"#su\",\"description\":\"点击百度一下\"}", stepId++));
        } else {
            // 通用配置
            steps.append(String.format("{\"stepId\":%d,\"action\":\"open_url\",\"target\":\"https://www.baidu.com\",\"description\":\"打开网页\"}", stepId));
        }

        steps.append("]}");
        return steps.toString();
    }

    /**
     * 从配置JSON解析步骤（供RpaTaskScheduler调用）
     */
    public List<RpaStep> parseSteps(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            throw new RuntimeException("任务配置为空");
        }
        try {
            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(configJson);

            List<RpaStep> steps = new ArrayList<>();
            com.alibaba.fastjson2.JSONArray stepsArray = json.getJSONArray("steps");

            if (stepsArray == null) {
                throw new RuntimeException("配置中未找到steps数组");
            }

            for (int i = 0; i < stepsArray.size(); i++) {
                com.alibaba.fastjson2.JSONObject stepJson = stepsArray.getJSONObject(i);
                RpaStep step = new RpaStep();

                step.setStepId(stepJson.getInteger("stepId"));
                step.setAction(stepJson.getString("action"));
                step.setTarget(stepJson.getString("target"));
                step.setValue(stepJson.getString("value"));
                step.setWaitTime(stepJson.getInteger("waitTime"));
                step.setDescription(stepJson.getString("description"));
                step.setFallbackTarget(stepJson.getString("fallbackTarget"));
                step.setRequired(stepJson.getBoolean("required"));

                Integer retryCount = stepJson.getInteger("retryCount");
                step.setRetryCount(retryCount != null ? retryCount : 3);

                // 解析图像模板（关键）
                if (stepJson.containsKey("imageTemplate")) {
                    step.setImageTemplate(stepJson.getString("imageTemplate"));
                }
                if (stepJson.containsKey("imageThreshold")) {
                    step.setImageThreshold(stepJson.getDouble("imageThreshold"));
                } else {
                    step.setImageThreshold(0.8);  // 默认阈值
                }

                steps.add(step);
            }

            return steps;
        } catch (Exception e) {
            log.error("解析步骤失败: {}", configJson, e);
            throw new RuntimeException("解析任务步骤失败: " + e.getMessage());
        }
    }

    /**
     * 步骤重规划（暂未实现，预留接口）
     */
    public List<RpaStep> replanSteps(String originalDescription,
                                     List<StepResult> completedSteps,
                                     StepResult failure,
                                     String currentUrl) {
        log.warn("步骤重规划功能暂未实现");
        return null;
    }
}