package com.insurancemegacorp.policymcpserver.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Metrics collector for vector store caching operations.
 *
 * Provides observability into:
 * - Cache hit/miss rates
 * - Cache warming success/failure rates
 * - Response time distributions
 * - Document fetch counts
 */
@Component
public class VectorCacheMetrics {

    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheWarmingSuccess;
    private final Counter cacheWarmingFailures;
    private final Counter documentsFromCache;
    private final Counter documentsFromPrimary;
    private final Counter documentsCached;
    private final Timer cacheQueryTime;
    private final Timer primaryQueryTime;

    public VectorCacheMetrics(MeterRegistry registry) {
        // Cache hit/miss counters
        this.cacheHits = Counter.builder("vectorstore.cache.hits")
                .description("Number of cache hits")
                .tag("store", "gemfire")
                .register(registry);

        this.cacheMisses = Counter.builder("vectorstore.cache.misses")
                .description("Number of cache misses")
                .tag("store", "gemfire")
                .register(registry);

        // Cache warming counters
        this.cacheWarmingSuccess = Counter.builder("vectorstore.cache.warming.success")
                .description("Successful cache warming operations")
                .register(registry);

        this.cacheWarmingFailures = Counter.builder("vectorstore.cache.warming.failures")
                .description("Failed cache warming operations")
                .register(registry);

        // Document counters
        this.documentsFromCache = Counter.builder("vectorstore.documents.retrieved")
                .description("Documents retrieved from stores")
                .tag("source", "cache")
                .register(registry);

        this.documentsFromPrimary = Counter.builder("vectorstore.documents.retrieved")
                .description("Documents retrieved from stores")
                .tag("source", "primary")
                .register(registry);

        this.documentsCached = Counter.builder("vectorstore.documents.cached")
                .description("Documents added to cache during warming")
                .register(registry);

        // Query timing
        this.cacheQueryTime = Timer.builder("vectorstore.query.duration")
                .description("Time spent querying vector stores")
                .tag("store", "cache")
                .register(registry);

        this.primaryQueryTime = Timer.builder("vectorstore.query.duration")
                .description("Time spent querying vector stores")
                .tag("store", "primary")
                .register(registry);
    }

    /**
     * Record a cache hit event
     */
    public void recordCacheHit(int documentCount) {
        cacheHits.increment();
        documentsFromCache.increment(documentCount);
    }

    /**
     * Record a cache miss event
     */
    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    /**
     * Record documents retrieved from primary store
     */
    public void recordPrimaryRetrieval(int documentCount) {
        documentsFromPrimary.increment(documentCount);
    }

    /**
     * Record successful cache warming
     */
    public void recordCacheWarmingSuccess(int documentCount) {
        cacheWarmingSuccess.increment();
        documentsCached.increment(documentCount);
    }

    /**
     * Record failed cache warming
     */
    public void recordCacheWarmingFailure() {
        cacheWarmingFailures.increment();
    }

    /**
     * Record cache query execution time
     */
    public void recordCacheQueryTime(long durationMillis) {
        cacheQueryTime.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Record primary store query execution time
     */
    public void recordPrimaryQueryTime(long durationMillis) {
        primaryQueryTime.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Get cache hit rate (hits / total requests)
     * Note: Calculated by metrics system, not stored here
     */
    public double getCacheHitRate() {
        double hits = cacheHits.count();
        double total = hits + cacheMisses.count();
        return total > 0 ? hits / total : 0.0;
    }

    /**
     * Get total number of successful warming operations
     */
    public long getWarmingSuccesses() {
        return (long) cacheWarmingSuccess.count();
    }

    /**
     * Get total number of documents cached via warming
     */
    public long getTotalDocumentsCached() {
        return (long) documentsCached.count();
    }

    /**
     * Get total cache hits
     */
    public long getCacheHits() {
        return (long) cacheHits.count();
    }

    /**
     * Get total cache misses
     */
    public long getCacheMisses() {
        return (long) cacheMisses.count();
    }
}
