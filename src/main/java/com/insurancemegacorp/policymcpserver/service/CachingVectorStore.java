package com.insurancemegacorp.policymcpserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * A read-through caching decorator for VectorStore.
 * 
 * This decorator wraps a GemFire cache and a Postgres primary store to provide:
 * - Fast cache-first retrieval
 * - Proactive cache warming on misses (over-fetching with configurable multiplier)
 * - Read-only cache (writes go directly to primary store)
 */
public class CachingVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(CachingVectorStore.class);

    private final VectorStore cache;      // GemFire
    private final VectorStore primary;    // Postgres pgvector
    private final int cacheMultiplier;

    public CachingVectorStore(VectorStore cache, VectorStore primary, int cacheMultiplier) {
        this.cache = cache;
        this.primary = primary;
        this.cacheMultiplier = cacheMultiplier;
        logger.info("CachingVectorStore initialized with cache multiplier: {}", cacheMultiplier);
    }

    @Override
    public void add(@NonNull List<Document> documents) {
        // Write path: only write to primary store
        // Cache remains read-only and is populated on read misses
        logger.debug("Adding {} documents to primary store", documents.size());
        primary.add(documents);
    }

    @Override
    public void delete(@NonNull List<String> idList) {
        // Delete from both primary and cache to maintain consistency
        logger.debug("Deleting {} documents from both primary and cache", idList.size());
        primary.delete(idList);
        cache.delete(idList);
    }

    @Override
    public void delete(@NonNull Filter.Expression filterExpression) {
        // Delete from both primary and cache using filter expression
        logger.debug("Deleting documents by filter from both primary and cache");
        primary.delete(filterExpression);
        cache.delete(filterExpression);
    }

    @Override
    @NonNull
    public List<Document> similaritySearch(@NonNull SearchRequest request) {
        int originalTopK = request.getTopK();
        
        // Try cache first
        logger.debug("Attempting cache lookup for query: '{}' with topK={}", 
                    request.getQuery(), originalTopK);
        
        List<Document> cachedResult = cache.similaritySearch(request);
        
        if (!cachedResult.isEmpty()) {
            logger.debug("Cache HIT: Found {} documents in cache", cachedResult.size());
            return cachedResult;
        }
        
        // Cache miss - query primary store with over-fetching
        logger.debug("Cache MISS: Querying primary store with over-fetching");
        
        int newTopK = originalTopK * cacheMultiplier;
        
        SearchRequest largerRequest = SearchRequest.builder()
                .query(request.getQuery())
                .topK(newTopK)
                .similarityThreshold(request.getSimilarityThreshold())
                .filterExpression(request.getFilterExpression())
                .build();

        List<Document> primaryResult = primary.similaritySearch(largerRequest);
        
        logger.debug("Primary store returned {} documents (requested {})", 
                    primaryResult.size(), newTopK);

        // Warm the cache with over-fetched results
        if (!primaryResult.isEmpty()) {
            logger.debug("Warming cache with {} documents", primaryResult.size());
            try {
                cache.add(primaryResult);
            } catch (Exception e) {
                logger.warn("Failed to warm cache: {}", e.getMessage());
                // Continue even if cache warming fails - return results from primary
            }
        }

        // Return only the originally requested number of documents
        if (primaryResult.size() > originalTopK) {
            logger.debug("Returning {} documents (trimmed from {})", 
                        originalTopK, primaryResult.size());
            return primaryResult.subList(0, originalTopK);
        }

        return primaryResult;
    }
}

