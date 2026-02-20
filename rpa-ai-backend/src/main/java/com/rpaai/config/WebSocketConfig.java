package com.rpaai.config;

import com.rpaai.websocket.BrowserAgentHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(browserAgentHandler(), "/ws/browser-agent")
                .setAllowedOrigins("*"); // 生产环境应限制域名
    }

    @Bean
    public BrowserAgentHandler browserAgentHandler() {
        return new BrowserAgentHandler();
    }
}