package com.rpaai.controller;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "http://localhost:5173")
public class TestController {

    @Autowired
    private ChatLanguageModel chatModel;

    @GetMapping("/ai")
    public String testAI(@RequestParam("question") String question) {  // ✅ 添加 "question" 参数名
        return "🤖 AI回复: " + chatModel.generate(question);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}