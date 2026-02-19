package com.rpaai.core.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DashScopeChatModel implements ChatLanguageModel {

    private final Generation generation = new Generation();
    private final String apiKey;
    private final String modelName;

    public DashScopeChatModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public String generate(String prompt) {
        log.debug("调用DashScope生成文本，模型: {}, 提示: {}", modelName, prompt);

        try {
            GenerationParam param = GenerationParam.builder()
                    .model(modelName)
                    .apiKey(apiKey)
                    .prompt(prompt)
                    .build();

            GenerationResult result = generation.call(param);
            String responseText = result.getOutput().getText();

            log.debug("DashScope响应: {}", responseText);
            return responseText;

        } catch (Exception e) {
            log.error("DashScope API调用失败", e);
            return "AI调用失败: " + e.getMessage();
        }
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        // 简化实现：取最后一条用户消息
        String lastMessage = messages.stream()
                .filter(msg -> msg.type() == dev.langchain4j.data.message.ChatMessageType.USER)
                .map(ChatMessage::text)
                .findFirst()
                .orElse("");

        String response = generate(lastMessage);

        return Response.from(
                AiMessage.from(response),
                new TokenUsage(0, 0) // 实际项目中应解析真实token使用量
        );
    }
}