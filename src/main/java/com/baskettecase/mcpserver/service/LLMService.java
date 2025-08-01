package com.baskettecase.mcpserver.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 🤖 Large Language Model Service for Insurance MegaCorp
 * 
 * <p>This service provides chat model operations for the insurance policy RAG pipeline,
 * including query expansion and contextual answer generation. It supports both OpenAI
 * and Ollama models through Spring AI's atomic profile architecture.</p>
 * 
 * <h3>Core Capabilities:</h3>
 * <ul>
 *   <li>📝 Query expansion for improved vector search results</li>
 *   <li>💬 Context-aware answer generation from retrieved documents</li>
 *   <li>🔧 Model connectivity testing and health checks</li>
 * </ul>
 * 
 * <h3>Model Configuration:</h3>
 * <p>The actual chat model is configured via Spring profiles:
 * <ul>
 *   <li>--chat-openai: Uses OpenAI models (gpt-4.1-nano, etc.)</li>
 *   <li>--chat-ollama: Uses local Ollama models (phi3, llama, etc.)</li>
 * </ul>
 * The service uses Spring AI's ChatModel abstraction for transparent model switching.</p>
 * 
 * <h3>Usage in RAG Pipeline:</h3>
 * <ol>
 *   <li>User query → {@link #expandQuery(String)} → Enhanced search terms</li>
 *   <li>Vector search → Retrieved documents → {@link #generateAnswer(String, List)} → Final answer</li>
 * </ol>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 * @see org.springframework.ai.chat.model.ChatModel
 * @see com.baskettecase.mcpserver.service.VectorStoreService
 */
@Service
public class LLMService {

    /**
     * The underlying chat model (configured via atomic profiles)
     */
    private final ChatModel chatModel;

    /**
     * Constructs a new LLMService with the configured chat model.
     * The actual model (OpenAI or Ollama) is determined by active Spring profiles.
     * 
     * @param chatModel the chat model instance (configured via atomic profiles)
     */
    @Autowired
    public LLMService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Expand a user query to improve vector search results
     * @param originalQuery The original user question
     * @return Expanded query with additional context and keywords
     */
    public String expandQuery(String originalQuery) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Original query cannot be null or empty");
        }

        String expansionPrompt = """
            You are a query expansion assistant for an insurance policy system.
            
            Your task is to expand the following user question by adding relevant keywords, 
            synonyms, and related terms that would help find relevant insurance policy documents.
            
            Guidelines:
            - Keep the expanded query concise (under 100 words)
            - Add insurance-specific terminology
            - Include synonyms and related concepts
            - Focus on policy-related keywords
            - Do not change the original intent
            
            Original question: {question}
            
            Expanded query:""";

        PromptTemplate template = new PromptTemplate(expansionPrompt);
        Prompt prompt = template.create(Map.of("question", originalQuery));
        
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText().trim();
    }

    /**
     * Generate an answer based on retrieved context documents
     * @param originalQuery The original user question
     * @param contextDocuments List of relevant documents from vector search
     * @return Generated answer based solely on the provided context
     */
    public String generateAnswer(String originalQuery, List<Document> contextDocuments) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("Original query cannot be null or empty");
        }
        if (contextDocuments == null || contextDocuments.isEmpty()) {
            return "I don't have enough information in the available documents to answer your question about: " + originalQuery;
        }

        // Combine all document content into context
        String context = contextDocuments.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));

        String answerPrompt = """
            You are an insurance policy assistant. Answer the user's question based SOLELY on the provided context.
            
            Important guidelines:
            - Only use information from the provided context
            - If the context doesn't contain enough information, say so clearly
            - Provide clear, helpful explanations using simple language
            - Reference specific policy details when available
            - Do not make assumptions or add information not in the context
            
            Context:
            {context}
            
            User Question: {question}
            
            Answer:""";

        PromptTemplate template = new PromptTemplate(answerPrompt);
        Prompt prompt = template.create(Map.of(
            "context", context,
            "question", originalQuery
        ));
        
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText().trim();
    }

    /**
     * Simple test method to verify LLM connectivity
     * @return A simple response to verify the service is working
     */
    public String testConnection() {
        try {
            String modelType = chatModel.getClass().getSimpleName();
            ChatResponse response = chatModel.call(new Prompt("Confirm you are working."));
            return String.format("LLM service is working using %s. Response: %s", 
                modelType, response.getResult().getOutput().getText());
        } catch (Exception e) {
            return "LLM service error: " + e.getMessage();
        }
    }
}