package com.baskettecase.mcpserver.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Separate service for testing AI models to avoid circular dependencies
 * This service is not injected into other tool services to break the cycle
 */
@Service
public class AIModelTestService {

    private final ApplicationContext applicationContext;

    @Autowired
    public AIModelTestService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Test the AI model connectivity for LLM and Embedding services
     * @return Status of both LLM and Embedding model connections
     */
    @Tool(description = "Test AI model connectivity including chat and embedding models")
    public String testAIModels() {
        StringBuilder result = new StringBuilder();
        result.append("=== AI Model Connectivity Test ===\n\n");
        
        // Test LLM Service
        result.append("🤖 Chat Model Test:\n");
        try {
            LLMService llmService = applicationContext.getBean(LLMService.class);
            String llmResult = llmService.testConnection();
            result.append("✅ ").append(llmResult).append("\n\n");
        } catch (Exception e) {
            result.append("❌ LLM Error: ").append(e.getMessage()).append("\n\n");
        }
        
        // Test Embedding Service
        result.append("🔢 Embedding Model Test:\n");
        try {
            EmbeddingService embeddingService = applicationContext.getBean(EmbeddingService.class);
            String embeddingResult = embeddingService.testConnection();
            result.append("✅ ").append(embeddingResult).append("\n\n");
        } catch (Exception e) {
            result.append("❌ Embedding Error: ").append(e.getMessage()).append("\n\n");
        }
        
        result.append("Test completed. Check results above for any issues.");
        return result.toString();
    }
}