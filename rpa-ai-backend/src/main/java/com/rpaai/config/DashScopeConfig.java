package com.rpaai.config;

import com.rpaai.core.ai.DashScopeChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class DashScopeConfig {

    @Value("${rpa.ai.llm.api-key:}")
    private String apiKey;

    @Value("${rpa.ai.llm.model:qwen-turbo}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("⚠️  DashScope API Key未配置，将使用MOCK模式");
            log.warn("请访问 https://dashscope.console.aliyun.com/ 获取API-KEY");

            // ✅ 修正：实现两个抽象方法
            return new ChatLanguageModel() {
                @Override
                public String generate(String prompt) {
                    return "【MOCK】API Key未配置，无法调用真实AI模型。请配置DashScope API Key后重启服务。";
                }

                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    return Response.from(
                            AiMessage.from("【MOCK】API Key未配置，无法调用真实AI模型。"),
                            new TokenUsage(0, 0)
                    );
                }
            };
        }

        log.info("✅ 初始化DashScope ChatModel，模型: {}", modelName);
        return new DashScopeChatModel(apiKey, modelName);
    }
}