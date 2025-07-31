package com.baskettecase.mcpserver.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for LLM operations including query expansion and answer generation
 * Uses Ollama for local profiles, OpenAI for cloud profiles
 */
@Service
public class LLMService {

    private final ChatModel chatModel;

    @Autowired
    public LLMService(@Qualifier("ollamaChatModel") ChatModel chatModel) {
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
            .map(doc -> doc.getText())
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
            ChatResponse response = chatModel.call(new Prompt("Say 'LLM service is working' in exactly those words."));
            return String.format("LLM service is working using %s. Response: %s", 
                modelType, response.getResult().getOutput().getText().trim());
        } catch (Exception e) {
            return "LLM service error: " + e.getMessage();
        }
    }
}