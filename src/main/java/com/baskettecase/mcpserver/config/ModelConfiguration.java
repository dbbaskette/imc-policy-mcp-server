package com.baskettecase.mcpserver.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Chat and Embedding models based on active profiles.
 * 
 * Spring AI 1.0.0 uses auto-configuration based on application properties.
 * Model selection is handled through property-based disabling to avoid
 * circular dependencies with tool registration.
 * 
 * For local profiles (local-sse, local-stdio):
 * - Both Ollama and OpenAI models are auto-configured based on available properties
 * - Services use @Qualifier to select specific model implementations
 * - Priority is determined at the service level, not configuration level
 */
@Configuration
public class ModelConfiguration {
    // Spring AI auto-configuration handles model instantiation
    // Service classes handle model selection via @Qualifier annotations
}