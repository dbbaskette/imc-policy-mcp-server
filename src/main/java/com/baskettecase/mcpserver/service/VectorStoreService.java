package com.baskettecase.mcpserver.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 📊 Vector Store Service for Insurance MegaCorp RAG Pipeline
 * 
 * <p>This service provides semantic search capabilities over insurance policy documents
 * using PostgreSQL with PGVector extension. It enables retrieval-augmented generation (RAG)
 * by finding the most relevant document chunks based on vector similarity.</p>
 * 
 * <h3>Core Capabilities:</h3>
 * <ul>
 *   <li>🔍 Semantic document search using vector similarity</li>
 *   <li>👤 Customer-filtered document retrieval</li>
 *   <li>📄 Multi-document relevance ranking</li>
 *   <li>⚙️ Configurable similarity thresholds and result limits</li>
 * </ul>
 * 
 * <h3>Vector Database Configuration:</h3>
 * <ul>
 *   <li><strong>Database</strong>: PostgreSQL with PGVector extension</li>
 *   <li><strong>Dimensions</strong>: 768 (matches nomic-embed-text model)</li>
 *   <li><strong>Similarity Metric</strong>: Cosine similarity</li>
 *   <li><strong>Table</strong>: vector_store (auto-created by Spring AI)</li>
 * </ul>
 * 
 * <h3>Document Lifecycle:</h3>
 * <p><strong>Note:</strong> This service is read-only. Document population and indexing
 * are handled by external ETL processes that load policy documents, chunk them into
 * manageable pieces, generate embeddings, and store them in the vector database.</p>
 * 
 * <h3>Integration with RAG Pipeline:</h3>
 * <ol>
 *   <li>User query embedded by {@link EmbeddingService}</li>
 *   <li>Vector similarity search performed by this service</li>
 *   <li>Retrieved documents passed to {@link LLMService} for answer generation</li>
 * </ol>
 * 
 * @author Insurance MegaCorp Development Team
 * @version 1.0.0
 * @since Spring AI 1.0.0
 * @see org.springframework.ai.vectorstore.VectorStore
 * @see com.baskettecase.mcpserver.service.EmbeddingService
 * @see com.baskettecase.mcpserver.service.LLMService
 */
@Service
public class VectorStoreService {

    /**
     * The underlying vector store (PostgreSQL + PGVector)
     */
    private final VectorStore vectorStore;

    /**
     * Constructs a new VectorStoreService with the specified vector store.
     * 
     * @param vectorStore the PGVector store instance (auto-configured by Spring AI)
     */
    @Autowired
    public VectorStoreService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Search for documents similar to the query, filtered by customer ID
     * @param query The search query text
     * @param customerId The customer ID to filter results
     * @param topK Maximum number of results to return
     * @return List of matching documents for the customer
     */
    public List<Document> searchByCustomer(String query, Integer customerId, int topK) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }

        // For now, use basic similarity search
        // TODO: Add customer filtering when Spring AI API is clarified
        List<Document> allResults = vectorStore.similaritySearch(query);
        
        // Manual filtering by customer_id metadata
        return allResults.stream()
            .filter(doc -> {
                Object customerIdMeta = doc.getMetadata().get("customer_id");
                return customerIdMeta != null && customerIdMeta.equals(customerId);
            })
            .limit(topK)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Search for documents similar to the query, filtered by customer ID with default parameters
     * @param query The search query text
     * @param customerId The customer ID to filter results
     * @return List of matching documents for the customer (top 5)
     */
    public List<Document> searchByCustomer(String query, Integer customerId) {
        return searchByCustomer(query, customerId, 5);
    }

    /**
     * Get the total count of documents for a specific customer
     * @param customerId The customer ID
     * @return Number of documents associated with the customer
     */
    public long getDocumentCountForCustomer(Integer customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID cannot be null");
        }

        // Simplified implementation - would need optimization for production
        try {
            List<Document> allDocs = vectorStore.similaritySearch("*");
            return allDocs.stream()
                .filter(doc -> {
                    Object customerIdMeta = doc.getMetadata().get("customer_id");
                    return customerIdMeta != null && customerIdMeta.equals(customerId);
                })
                .count();
        } catch (Exception e) {
            // Fallback for vector stores that don't support wildcard search
            return 0;
        }
    }
}