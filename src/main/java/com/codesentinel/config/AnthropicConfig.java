package com.codesentinel.config;

import com.codesentinel.agent.ReviewAgent;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Anthropic Claude LLM and LangChain4j integration.
 * Securely loads ANTHROPIC_API_KEY from environment without logging secrets.
 */
@Configuration
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${anthropic.model-name:claude-sonnet-5-5}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        String key = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : "dummy-anthropic-key-for-test";

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("ANTHROPIC_API_KEY is not set. Model initialized with dummy placeholder for testing.");
        } else {
            log.info("Initializing Anthropic Claude model: {}", modelName);
        }

        return AnthropicChatModel.builder()
                .apiKey(key)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(60))
                .logRequests(false)  // Guardrail: Never log API keys or tokens
                .logResponses(false)
                .build();
    }

    @Bean
    public ReviewAgent reviewAgent(ChatLanguageModel chatLanguageModel, com.codesentinel.agent.AgentTools agentTools) {
        log.info("Building LangChain4j ReviewAgent with ChatLanguageModel and AgentTools");
        return AiServices.builder(ReviewAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(agentTools)
                .build();
    }
}
