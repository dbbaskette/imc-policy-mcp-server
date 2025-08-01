package com.baskettecase.mcpserver.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Service for testing AI model connectivity.
 * This service is not injected into other tool services to avoid circular dependencies.
 */
@Service
public class AIModelTestService {

    private final ApplicationContext applicationContext;

    @Autowired
    public AIModelTestService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Tests the connectivity for the currently configured Chat and Embedding models.
     * @return A status report of the AI model connections.
     */
    @Tool(description = "Test AI model connectivity for both chat and embedding models")
    public String testAIModels() {
        StringBuilder result = new StringBuilder();
        result.append("=== AI Model Connectivity Test ===\n\n");

        // Test LLM Service
        result.append("🤖 Chat Model Test:\n");
        try {
            LLMService llmService = applicationContext.getBean(LLMService.class);
            result.append("✅ ").append(llmService.testConnection()).append("\n\n");
        } catch (Exception e) {
            result.append("❌ LLM Service Error: ").append(e.getMessage()).append("\n\n");
        }

        // Test Embedding Service
        result.append("🔢 Embedding Model Test:\n");
        try {
            EmbeddingService embeddingService = applicationContext.getBean(EmbeddingService.class);
            result.append("✅ ").append(embeddingService.testConnection()).append("\n\n");
        } catch (Exception e) {
            result.append("❌ Embedding Service Error: ").append(e.getMessage()).append("\n\n");
        }

        result.append("Test completed. Check results above for any issues.");
        return result.toString();
    }
}