package com.baskettecase.mcpserver.config;

import org.springframework.context.annotation.Configuration;

/**
 * Model configuration for the MCP server.
 * 
 * Model creation is handled entirely by Spring AI auto-configuration based on
 * the enabled/disabled properties in our atomic profile files:
 * - application-chat-openai.properties
 * - application-chat-ollama.properties  
 * - application-embed-openai.properties
 * - application-embed-ollama.properties
 * 
 * This approach lets Spring AI handle the complex bean creation and API changes
 * while we control which models are active through simple property flags.
 */
@Configuration
public class ModelConfiguration {
    // Spring AI auto-configuration handles all model bean creation
    // based on spring.ai.*.enabled properties
}