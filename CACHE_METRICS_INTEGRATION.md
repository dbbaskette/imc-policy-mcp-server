# Cache Metrics Integration

## Overview

The cache warming API now fully integrates with Spring Boot Actuator metrics, providing complete observability into cache operations. Metrics are automatically recorded for all cache warming operations (both API-triggered and query-triggered).

## What's Tracked

### Automatic Metrics Recording

Every time you call the cache warming API, the following metrics are automatically recorded:

1. **Documents Cached Count** - Total number of documents added to cache
2. **Warming Success Count** - Number of successful warming operations
3. **Warming Failure Count** - Number of failed warming operations

### Where Metrics Are Recorded

Metrics are recorded in three places:

1. **DirectGemFireCacheWarmer** - Records metrics when warming via API or after query misses
2. **CachingVectorStore** - Records cache hits, misses, and query timing
3. **VectorCacheMetrics** - Provides aggregation and retrieval methods

## API Response with Metrics

### Example Request
```bash
curl -X POST https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/api/cache/warm/100002
```

### Example Response
```json
{
  "success": true,
  "message": "Cache warming completed successfully",
  "customerId": 100002,
  "documentsWarmed": 12,
  "cacheStats": {
    "totalWarmingOperations": 2,
    "totalDocumentsCached": 19,
    "cacheHitRate": "0.0%"
  }
}
```

### Response Fields Explained

- **documentsWarmed**: Number of documents cached **in this specific operation**
- **cacheStats.totalWarmingOperations**: Cumulative count since application startup
- **cacheStats.totalDocumentsCached**: Cumulative count of all documents cached since startup
- **cacheStats.cacheHitRate**: Percentage of queries served from cache vs. primary store

## Viewing Metrics in the Cache UI

The cache stats are designed to integrate with monitoring UIs. You can display them to users to show:

### Chatbot Integration Example

```javascript
async function warmCacheForUser(customerId) {
  const response = await fetch(
    `https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/api/cache/warm/${customerId}`,
    { method: 'POST' }
  );

  const result = await response.json();

  if (result.success) {
    // Show user-friendly message
    showNotification(
      `✅ Cache warmed: ${result.documentsWarmed} documents loaded. ` +
      `All future queries will be faster!`
    );

    // Update UI stats panel
    updateCacheStatsPanel({
      hitRate: result.cacheStats.cacheHitRate,
      totalDocuments: result.cacheStats.totalDocumentsCached,
      warmingOperations: result.cacheStats.totalWarmingOperations
    });
  }
}
```

### UI Display Example

```
┌─────────────────────────────────────────┐
│ Cache Status                            │
├─────────────────────────────────────────┤
│ ✅ Cache Enabled                        │
│                                         │
│ Documents in Cache: 19                  │
│ Cache Hit Rate: 45.5%                   │
│ Warming Operations: 2                   │
│                                         │
│ Last Warming: Customer 100002           │
│ Documents Loaded: 12                    │
└─────────────────────────────────────────┘
```

## Detailed Metrics via Actuator

For operational monitoring and dashboards, use Spring Boot Actuator endpoints:

### Key Metrics Endpoints

```bash
# Documents cached during warming operations
curl https://.../actuator/metrics/vectorstore.documents.cached
# Returns: { "value": 19.0 }

# Number of successful warming operations
curl https://.../actuator/metrics/vectorstore.cache.warming.success
# Returns: { "value": 2.0 }

# Number of failed warming operations
curl https://.../actuator/metrics/vectorstore.cache.warming.failures
# Returns: { "value": 0.0 }

# Cache hit count
curl https://.../actuator/metrics/vectorstore.cache.hits
# Returns: { "value": 15.0 }

# Cache miss count
curl https://.../actuator/metrics/vectorstore.cache.misses
# Returns: { "value": 8.0 }

# Documents retrieved from cache
curl https://.../actuator/metrics/vectorstore.documents.retrieved?tag=source:cache
# Returns: { "value": 45.0 }

# Documents retrieved from primary store
curl https://.../actuator/metrics/vectorstore.documents.retrieved?tag=source:primary
# Returns: { "value": 23.0 }

# Query timing for cache
curl https://.../actuator/metrics/vectorstore.query.duration?tag=store:cache
# Returns: { "mean": 23.5, "max": 150.0, ... }

# Query timing for primary store
curl https://.../actuator/metrics/vectorstore.query.duration?tag=store:primary
# Returns: { "mean": 450.2, "max": 1200.0, ... }
```

### Prometheus Integration

All these metrics are automatically exposed in Prometheus format:

```bash
curl https://.../actuator/prometheus | grep vectorstore
```

Output:
```
# HELP vectorstore_documents_cached_total Documents added to cache during warming
# TYPE vectorstore_documents_cached_total counter
vectorstore_documents_cached_total 19.0

# HELP vectorstore_cache_warming_success_total Successful cache warming operations
# TYPE vectorstore_cache_warming_success_total counter
vectorstore_cache_warming_success_total 2.0

# HELP vectorstore_cache_hits_total Number of cache hits
# TYPE vectorstore_cache_hits_total counter
vectorstore_cache_hits_total{store="gemfire"} 15.0

# ... and more
```

## Benefits for Users

### 1. Transparency
Users can see exactly what happened when they trigger cache warming:
- "12 documents loaded into cache"
- "Cache hit rate improved from 20% to 65%"

### 2. Confidence
Metrics provide proof that caching is working:
- Total documents cached keeps growing
- Hit rate increases over time
- Query times show cache is faster

### 3. Troubleshooting
If cache warming fails, users get immediate feedback:
```json
{
  "success": false,
  "message": "Cache warming failed - no documents warmed",
  "customerId": 100001,
  "documentsWarmed": 0
}
```

### 4. Operational Visibility
Admins can monitor cache health:
- Warming success/failure rates
- Cache hit rate trends
- Performance improvements (cache vs. primary query times)

## Testing the Integration

### Test Scenario 1: Warm Cache for Multiple Customers

```bash
# Warm customer 100002 - expect 12 documents
curl -X POST .../api/cache/warm/100002 | jq '.documentsWarmed'
# Output: 12

# Check cumulative stats
curl -X POST .../api/cache/warm/100003 | jq '.cacheStats'
# Output:
# {
#   "totalWarmingOperations": 2,
#   "totalDocumentsCached": 19,
#   "cacheHitRate": "0.0%"
# }
```

### Test Scenario 2: Verify Metrics in Actuator

```bash
# Check total documents cached
curl -s .../actuator/metrics/vectorstore.documents.cached | jq '.measurements[0].value'
# Output: 19.0

# Check warming success count
curl -s .../actuator/metrics/vectorstore.cache.warming.success | jq '.measurements[0].value'
# Output: 2.0
```

### Test Scenario 3: Monitor Cache Hit Rate After Queries

```bash
# Warm cache for customer 100002
curl -X POST .../api/cache/warm/100002

# Run some queries (via MCP or debug endpoint)
# Then check hit rate
curl -s .../api/cache/stats | jq '.cacheHitRate'
# Output: "65.5%"
```

## Code Reference

### Files Updated

1. **DirectGemFireCacheWarmer.java** ([line 33](src/main/java/com/insurancemegacorp/policymcpserver/service/DirectGemFireCacheWarmer.java#L33))
   - Added `VectorCacheMetrics` dependency
   - Records metrics on warming success/failure

2. **VectorCacheMetrics.java** ([line 141-167](src/main/java/com/insurancemegacorp/policymcpserver/service/VectorCacheMetrics.java#L141))
   - Added getter methods for counts
   - `getWarmingSuccesses()`, `getTotalDocumentsCached()`, etc.

3. **CacheController.java** ([line 54-76](src/main/java/com/insurancemegacorp/policymcpserver/controller/CacheController.java#L54))
   - Captures metrics before/after warming
   - Includes stats in API response

## Summary

✅ **All cache warming operations now record metrics**
✅ **API responses include real-time stats**
✅ **Spring Actuator exposes detailed metrics**
✅ **Ready for Prometheus/Grafana integration**
✅ **Users see transparent cache operation feedback**
