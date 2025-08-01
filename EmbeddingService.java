package com.baskettecase.mcpserver.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AiModelConfiguration {

    // --- Local (Ollama) Profile Beans ---

    @Bean
    @Profile("local")
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        return new OllamaApi(baseUrl);
    }

    @Bean
    @Profile("local")
    public ChatModel ollamaChatModel(OllamaApi ollamaApi, @Value("${spring.ai.ollama.chat.options.model}") String model) {
        return new OllamaChatModel(ollamaApi, OllamaOptions.create().withModel(model));
    }

    @Bean
    @Profile("local")
    public EmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi, @Value("${spring.ai.ollama.embedding.options.model}") String model) {
        return new OllamaEmbeddingModel(ollamaApi).withDefaultOptions(OllamaOptions.create().withModel(model));
    }

    // --- Cloud (OpenAI) Profile Beans ---

    @Bean
    @Profile("cloud")
    public ChatModel openAiChatModel(OpenAiApi openAiApi, @Value("${spring.ai.openai.chat.options.model}") String model) {
        return new OpenAiChatModel(openAiApi, OpenAiChatOptions.builder().withModel(model).build());
    }

    @Bean
    @Profile("cloud")
    public EmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi, @Value("${spring.ai.openai.embedding.options.model}") String model) {
        return new OpenAiEmbeddingModel(openAiApi, OpenAiEmbeddingOptions.builder().withModel(model).build());
    }
}