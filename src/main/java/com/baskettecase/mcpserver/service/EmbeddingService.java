package com.baskettecase.mcpserver.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🔢 Text Embedding Service for Insurance MegaCorp
 * 
 * <p>This service handles text-to-vector conversion for the insurance policy RAG system.
 * It transforms natural language queries and documents into high-dimensional vectors
 * that can be used for semantic similarity search in the vector database.</p>
 * 
 * <h3>Core Capabilities:</h3>
 * <ul>
 *   <li>🔤 Text to vector embedding conversion</li>
 *   <li>📊 High-dimensional vector generation (768 dimensions)</li>
 *   <li>🧪 Embedding model connectivity testing</li>
 * </ul>
 * 
 * <h3>Model Configuration:</h3>
 * <p>The actual embedding model is configured via Spring profiles:
 * <ul>
 *   <li>--embed-openai: Uses OpenAI embeddings (text-embedding-3-small, etc.)</li>
 *   <li>--embed-ollama: Uses local Ollama embeddings (nomic-embed-text, etc.)</li>
 * </ul>
 * Models generate different vector dimensions: OpenAI (1536), Ollama (768).</p>
 * 
 * <h3>Integration with RAG Pipeline:</h3>
 * <p>User queries are embedded using this service, then compared against document
 * embeddings stored in the PostgreSQL+PGVector database to find the most relevant
 * insurance policy information.</p>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 * @see org.springframework.ai.embedding.EmbeddingModel
 * @see com.baskettecase.mcpserver.service.VectorStoreService
 */
@Service
public class EmbeddingService {

    /**
     * The underlying embedding model (configured via atomic profiles)
     */
    private final EmbeddingModel embeddingModel;

    /**
     * Constructs a new EmbeddingService with the configured embedding model.
     * The actual model (OpenAI or Ollama) is determined by active Spring profiles.
     * 
     * @param embeddingModel the embedding model instance (configured via atomic profiles)
     */
    @Autowired
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Convert text to embedding vector
     * @param text The text to embed
     * @return A list of doubles representing the embedding vector
     */
    public List<Double> embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text to embed cannot be null or empty");
        }
        EmbeddingResponse response = this.embeddingModel.embedForResponse(List.of(text));
        float[] embedding = response.getResult().getOutput();
        List<Double> result = new java.util.ArrayList<>();
        for (float f : embedding) {
            result.add((double) f);
        }
        return result;
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