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
 * A read-through caching decorator for VectorStore with operational metrics.
 *
 * This decorator wraps a GemFire cache and a Postgres primary store to provide:
 * - Fast cache-first retrieval
 * - Proactive cache warming on misses (over-fetching with configurable multiplier)
 * - Read-only cache (writes go directly to primary store)
 * - Comprehensive metrics for monitoring cache performance
 */
public class CachingVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(CachingVectorStore.class);

    private final VectorStore cache;      // GemFire
    private final VectorStore primary;    // Postgres pgvector
    private final int cacheMultiplier;
    private final VectorCacheMetrics metrics;
    private final DirectGemFireCacheWarmer directWarmer;

    public CachingVectorStore(VectorStore cache, VectorStore primary, int cacheMultiplier,
                              VectorCacheMetrics metrics, DirectGemFireCacheWarmer directWarmer) {
        this.cache = cache;
        this.primary = primary;
        this.cacheMultiplier = cacheMultiplier;
        this.metrics = metrics;
        this.directWarmer = directWarmer;
        logger.info("CachingVectorStore initialized with cache multiplier: {}", cacheMultiplier);
        logger.info("Direct GemFire cache warmer: {}", directWarmer.isEnabled() ? "ENABLED" : "DISABLED");
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

        // Try cache first - wrap in try-catch to gracefully fall back to primary if cache fails
        // GemFire VectorDB now supports filtering via "filter-query" parameter
        List<Document> cachedResult = List.of();
        try {
            logger.info("🔍 Attempting cache lookup for query: '{}' with topK={}",
                        request.getQuery(), originalTopK);

            long cacheStartTime = System.currentTimeMillis();
            cachedResult = cache.similaritySearch(request);
            long cacheEndTime = System.currentTimeMillis();

            metrics.recordCacheQueryTime(cacheEndTime - cacheStartTime);

            if (!cachedResult.isEmpty()) {
                logger.info("✅ Cache HIT: Found {} documents in cache ({}ms)",
                           cachedResult.size(), (cacheEndTime - cacheStartTime));
                metrics.recordCacheHit(cachedResult.size());
                return cachedResult;
            }
        } catch (Exception e) {
            logger.warn("⚠️  Cache query failed (falling back to primary): {}", e.getMessage());
            logger.debug("Cache error details", e);
            // Fall through to primary store query
        }

        // Cache miss or error - query primary store
        // NOTE: Over-fetching disabled since cache should provide all needed docs when warmed
        logger.info("❌ Cache MISS: Querying primary store (topK: {})", originalTopK);
        metrics.recordCacheMiss();

        // DISABLED: Over-fetching logic (no longer needed with proper cache warming)
        // int newTopK = originalTopK * cacheMultiplier;
        // When cache is properly warmed, we should get HITs every time, so no need to over-fetch

        SearchRequest primaryRequest = SearchRequest.builder()
                .query(request.getQuery())
                .topK(originalTopK)  // Use original topK, not multiplied
                .similarityThreshold(request.getSimilarityThreshold())
                .filterExpression(request.getFilterExpression())
                .build();

        long primaryStartTime = System.currentTimeMillis();
        List<Document> primaryResult = primary.similaritySearch(primaryRequest);
        long primaryEndTime = System.currentTimeMillis();

        metrics.recordPrimaryQueryTime(primaryEndTime - primaryStartTime);

        logger.info("📊 Primary store returned {} documents (requested {}, took {}ms)",
                    primaryResult.size(), originalTopK, (primaryEndTime - primaryStartTime));

        // Warm the cache - NEW APPROACH: Fetch ALL customer documents
        if (!primaryResult.isEmpty() && directWarmer.isEnabled()) {
            // Extract customer ID from filter expression if available
            Integer customerId = extractCustomerIdFromFilter(request.getFilterExpression());

            if (customerId != null) {
                logger.info("🔥 Warming cache with ALL documents for customer {}", customerId);
                long warmStartTime = System.currentTimeMillis();
                boolean warmingSuccess = directWarmer.warmCacheForCustomer(customerId);
                long warmEndTime = System.currentTimeMillis();

                if (warmingSuccess) {
                    // Query GemFire to see how many docs were actually warmed
                    int warmedCount = primaryResult.size(); // Use primary result size as approximation
                    metrics.recordCacheWarmingSuccess(warmedCount);
                    logger.info("✅ Cache warming SUCCESS for customer {} ({}ms)", customerId, (warmEndTime - warmStartTime));
                } else {
                    metrics.recordCacheWarmingFailure();
                    logger.warn("⚠️  Cache warming FAILED for customer {}", customerId);
                }
            } else {
                // Fallback to old similarity-based warming if no customer ID filter
                logger.debug("No customer filter found, using similarity-based warming with {} documents", primaryResult.size());
                boolean warmingSuccess = directWarmer.warmCache(primaryResult);

                if (warmingSuccess) {
                    metrics.recordCacheWarmingSuccess(primaryResult.size());
                } else {
                    metrics.recordCacheWarmingFailure();
                }
            }
        }

        // Return only the originally requested number of documents
        int documentsToReturn = Math.min(primaryResult.size(), originalTopK);
        metrics.recordPrimaryRetrieval(documentsToReturn);

        if (primaryResult.size() > originalTopK) {
            logger.debug("Returning {} documents (trimmed from {})",
                        originalTopK, primaryResult.size());
            return primaryResult.subList(0, originalTopK);
        }

        return primaryResult;
    }

    /**
     * Extract customer ID from filter expression.
     * Looks for eq("refnum1", value) pattern in the filter.
     *
     * @param filterExpression The filter expression to parse
     * @return Customer ID if found, null otherwise
     */
    private Integer extractCustomerIdFromFilter(Filter.Expression filterExpression) {
        if (filterExpression == null) {
            return null;
        }

        try {
            // Convert filter to string and parse it
            // FilterExpressionBuilder().eq("refnum1", 1001) produces: refnum1 == 1001
            String filterString = filterExpression.toString();
            logger.debug("Parsing filter expression: {}", filterString);

            // Look for pattern: refnum1 == <number>
            if (filterString.contains("refnum1")) {
                String[] parts = filterString.split("==");
                if (parts.length == 2) {
                    String valueStr = parts[1].trim();
                    // Remove any quotes if present
                    valueStr = valueStr.replace("'", "").replace("\"", "");
                    return Integer.parseInt(valueStr);
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to extract customer ID from filter: {}", e.getMessage());
        }

        return null;
    }
}

