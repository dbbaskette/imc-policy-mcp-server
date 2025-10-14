package com.insurancemegacorp.policymcpserver.controller;

import com.insurancemegacorp.policymcpserver.service.DirectGemFireCacheWarmer;
import com.insurancemegacorp.policymcpserver.service.VectorCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for cache management operations.
 * Provides endpoints to proactively warm the cache and check cache status.
 */
@RestController
@RequestMapping("/api/cache")
@CrossOrigin(origins = {"https://imc-chatbot.apps.tas-ndc.kuhn-labs.com", "http://localhost:*"})
public class CacheController {

    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);

    @Autowired
    private DirectGemFireCacheWarmer cacheWarmer;

    @Autowired
    private VectorCacheMetrics metrics;

    /**
     * Warm the cache for a specific customer.
     * Fetches ALL documents for the customer and loads them into the cache.
     *
     * This endpoint can be called proactively when a user logs in to ensure
     * all their documents are cached before they start querying.
     *
     * @param customerId The customer ID to warm cache for
     * @return Status of cache warming operation
     */
    @PostMapping("/warm/{customerId}")
    public ResponseEntity<Map<String, Object>> warmCache(@PathVariable Integer customerId) {
        logger.info("API request to warm cache for customer {}", customerId);

        if (!cacheWarmer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "success", false,
                            "message", "Cache warming is disabled - no credentials configured",
                            "customerId", customerId
                    ));
        }

        try {
            // Get metrics before warming
            long documentsCachedBefore = metrics.getTotalDocumentsCached();

            // Warm the cache
            boolean success = cacheWarmer.warmCacheForCustomer(customerId);

            // Get metrics after warming to show what happened
            long documentsCachedAfter = metrics.getTotalDocumentsCached();
            long warmedCount = documentsCachedAfter - documentsCachedBefore;
            long totalWarmings = metrics.getWarmingSuccesses();
            long totalWarmedDocs = metrics.getTotalDocumentsCached();

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Cache warming completed successfully",
                        "customerId", customerId,
                        "documentsWarmed", warmedCount,
                        "cacheStats", Map.of(
                                "totalWarmingOperations", totalWarmings,
                                "totalDocumentsCached", totalWarmedDocs,
                                "cacheHitRate", String.format("%.1f%%", metrics.getCacheHitRate() * 100)
                        )
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "success", false,
                                "message", "Cache warming failed - no documents warmed",
                                "customerId", customerId,
                                "documentsWarmed", 0
                        ));
            }

        } catch (Exception e) {
            logger.error("Cache warming API failed for customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Cache warming failed: " + e.getMessage(),
                            "customerId", customerId
                    ));
        }
    }

    /**
     * Get cache statistics and health status.
     *
     * Note: Detailed metrics are available via Spring Actuator at /actuator/metrics
     * This endpoint provides a summary view.
     *
     * @return Current cache metrics summary
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(Map.of(
                "cacheEnabled", cacheWarmer.isEnabled(),
                "cacheHitRate", String.format("%.2f%%", metrics.getCacheHitRate() * 100),
                "metricsEndpoint", "/actuator/metrics/vectorstore.*",
                "message", "Use /actuator/metrics for detailed metrics (hits, misses, timing, etc.)"
        ));
    }

    /**
     * ADMIN: Recreate GemFire index with filterable fields.
     * This will delete the existing index and recreate it with proper metadata field configuration.
     *
     * WARNING: All cached data will be lost and must be re-warmed.
     *
     * @return Status of recreation operation
     */
    @PostMapping("/admin/recreate-index")
    public ResponseEntity<Map<String, Object>> recreateIndex() {
        logger.warn("ADMIN: Request to recreate GemFire index - all cache data will be lost!");

        if (!cacheWarmer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "success", false,
                            "message", "Cache is disabled - no credentials configured"
                    ));
        }

        try {
            boolean success = cacheWarmer.recreateIndex();

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "GemFire index recreated successfully with filterable fields",
                        "fields", new String[]{"refnum1", "refnum2", "sourcePath", "timestamp"},
                        "note", "Cache is now empty - use /api/cache/warm/{customerId} to populate"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "success", false,
                                "message", "Failed to recreate index"
                        ));
            }

        } catch (Exception e) {
            logger.error("Failed to recreate GemFire index: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error recreating index: " + e.getMessage()
                    ));
        }
    }
}
