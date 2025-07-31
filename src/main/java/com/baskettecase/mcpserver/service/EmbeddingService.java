package com.baskettecase.mcpserver.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for text embedding operations
 * Uses Ollama for local profiles, OpenAI for cloud profiles
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Autowired
    public EmbeddingService(@Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Convert text to embedding vector
     * @param text The text to embed
     * @return Embedding vector as list of floats
     */
    public List<Double> embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        float[] embedding = response.getResults().get(0).getOutput();
        
        // Convert float[] to List<Double>
        List<Double> result = new java.util.ArrayList<>();
        for (float f : embedding) {
            result.add((double) f);
        }
        return result;
    }

    /**
     * Get the embedding dimension size
     * @return The dimension size of embeddings produced by this model
     */
    public int getEmbeddingDimensions() {
        // Test with a simple string to get dimensions
        try {
            List<Double> testEmbedding = embedText("test");
            return testEmbedding.size();
        } catch (Exception e) {
            // Default dimensions based on common models
            // OpenAI text-embedding-3-small: 1536
            // nomic-embed-text: 768
            return 768; // Conservative default
        }
    }

    /**
     * Simple test method to verify embedding model connectivity
     * @return A simple response to verify the service is working
     */
    public String testConnection() {
        try {
            String modelType = embeddingModel.getClass().getSimpleName();
            List<Double> embedding = embedText("test connection");
            return String.format("Embedding service is working using %s. Dimension: %d, First value: %.6f", 
                modelType, embedding.size(), embedding.get(0));
        } catch (Exception e) {
            return "Embedding service error: " + e.getMessage();
        }
    }
}