package com.baskettecase.mcpserver.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for querying the vector store with customer filtering.
 * Note: Document population is handled by external systems.
 */
@Service
public class VectorStoreService {

    private final VectorStore vectorStore;

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