package com.baskettecase.mcpserver.config;

import org.springframework.context.annotation.Configuration;

/**
 * Model configuration for the MCP server.
 * 
 * Local profiles (local-sse, local-stdio) use only Ollama models for simplicity.
 * This avoids bean conflicts and provides a clean development experience.
 * 
 * Services use @Qualifier("ollamaChatModel") and @Qualifier("ollamaEmbeddingModel")
 * to explicitly reference the Ollama model beans.
 */
@Configuration
public class ModelConfiguration {
    // All model configuration is handled through Spring AI auto-configuration
    // based on the spring.ai.ollama.* properties in application-local-*.properties
}