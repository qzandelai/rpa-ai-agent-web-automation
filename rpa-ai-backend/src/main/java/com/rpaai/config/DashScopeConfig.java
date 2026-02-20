package com.rpaai.config;

import com.rpaai.core.ai.DashScopeChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
public class DashScopeConfig {

    @Value("${rpa.ai.llm.api-key:}")
    private String apiKey;

    @Value("${rpa.ai.llm.model:qwen-turbo}")
    private String modelName;

    @Autowired
    private Environment env;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        // 检查当前激活的profile
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        log.info("当前激活的profiles: {}", profiles);

        // 如果apiKey为空，给出友好提示
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("⚠️  DashScope API Key未配置");
            log.warn("本地开发：创建 application-local.yml 并配置 api-key");
            log.warn("生产环境：设置环境变量 DASHSCOPE_API_KEY");
            log.warn("获取Key: https://dashscope.console.aliyun.com/");

            return createMockModel();
        }

        log.info("✅ 初始化DashScope ChatModel，模型: {}", modelName);
        return new DashScopeChatModel(apiKey, modelName);
    }

    private ChatLanguageModel createMockModel() {
        return new ChatLanguageModel() {
            @Override
            public String generate(String prompt) {
                return "【MOCK模式】API Key未配置。请检查：\n" +
                        "1. 本地开发：创建 src/main/resources/application-local.yml\n" +
                        "2. 添加配置：rpa.ai.llm.api-key: your_key\n" +
                        "3. 启动时添加参数：--spring.profiles.active=local";
            }

            @Override
            public Response<AiMessage> generate(List<ChatMessage> messages) {
                return Response.from(
                        AiMessage.from("【MOCK模式】"),
                        new TokenUsage(0, 0)
                );
            }
        };
    }
}