# Vector Store Cache Metrics

This document describes the operational metrics available for monitoring the GemFire vector store caching layer.

## Overview

The `VectorCacheMetrics` component provides comprehensive observability into cache performance through Spring Boot Actuator and Micrometer. All metrics are automatically registered and available via the `/actuator/metrics` endpoint.

## Available Metrics

### Cache Performance Metrics

#### `vectorstore.cache.hits`
- **Type:** Counter
- **Description:** Total number of cache hits (successful cache retrievals)
- **Tags:** `store=gemfire`
- **Use Case:** Calculate cache hit rate, monitor cache effectiveness

#### `vectorstore.cache.misses`
- **Type:** Counter
- **Description:** Total number of cache misses (queries requiring primary store access)
- **Tags:** `store=gemfire`
- **Use Case:** Calculate cache miss rate, identify cold cache scenarios

**Cache Hit Rate Formula:**
```
hit_rate = hits / (hits + misses)
```

### Cache Warming Metrics

#### `vectorstore.cache.warming.success`
- **Type:** Counter
- **Description:** Successful cache warming operations after misses
- **Use Case:** Monitor proactive cache population effectiveness

#### `vectorstore.cache.warming.failures`
- **Type:** Counter
- **Description:** Failed cache warming operations
- **Use Case:** Alert on cache infrastructure issues, identify GemFire problems

### Document Retrieval Metrics

#### `vectorstore.documents.retrieved`
- **Type:** Counter
- **Description:** Total documents retrieved from vector stores
- **Tags:**
  - `source=cache` - Documents served from GemFire cache
  - `source=primary` - Documents served from Postgres primary store
- **Use Case:** Monitor data flow, calculate cache efficiency

#### `vectorstore.documents.cached`
- **Type:** Counter
- **Description:** Total documents added to cache during warming operations
- **Use Case:** Monitor cache growth, understand over-fetch multiplier impact

### Query Performance Metrics

#### `vectorstore.query.duration`
- **Type:** Timer
- **Description:** Query execution time distribution
- **Tags:**
  - `store=cache` - Time to query GemFire cache
  - `store=primary` - Time to query Postgres primary store
- **Percentiles:** p50, p95, p99
- **Use Case:** Compare cache vs. primary store performance, identify slow queries

## Accessing Metrics

### HTTP Endpoint

All metrics are available via Spring Boot Actuator:

```bash
# List all available metrics
curl http://localhost:8080/actuator/metrics

# Get specific metric
curl http://localhost:8080/actuator/metrics/vectorstore.cache.hits

# Get metric with tags
curl http://localhost:8080/actuator/metrics/vectorstore.documents.retrieved?tag=source:cache
```

### Sample Response

```json
{
  "name": "vectorstore.cache.hits",
  "description": "Number of cache hits",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1247.0
    }
  ],
  "availableTags": [
    {
      "tag": "store",
      "values": ["gemfire"]
    }
  ]
}
```

## Configuration

### Enable Metrics Endpoint

Metrics are enabled by default. Configuration in `application.properties`:

```properties
# Expose metrics endpoint
management.endpoints.web.exposure.include=health,info,metrics

# Enable vector store metrics
management.metrics.enable.vectorstore=true
```

## Monitoring & Alerting

### Key Performance Indicators (KPIs)

1. **Cache Hit Rate**
   - Target: > 80%
   - Alert: < 60%
   - Action: Review cache multiplier, check cache capacity

2. **Cache Warming Success Rate**
   - Target: > 95%
   - Alert: < 90%
   - Action: Investigate GemFire connectivity, check error logs

3. **Query Performance Improvement**
   - Metric: `cache query time / primary query time`
   - Target: < 0.1 (10x faster)
   - Action: If cache not faster, review GemFire configuration

4. **Cache Warming Failures**
   - Target: 0 failures per hour
   - Alert: > 5 failures per hour
   - Action: Check GemFire health, network connectivity

### Sample Prometheus Queries

If exporting to Prometheus, use these queries:

```promql
# Cache hit rate (5-minute rate)
rate(vectorstore_cache_hits_total[5m]) /
(rate(vectorstore_cache_hits_total[5m]) + rate(vectorstore_cache_misses_total[5m]))

# Cache warming failure rate
rate(vectorstore_cache_warming_failures_total[5m])

# 95th percentile cache query time
histogram_quantile(0.95, rate(vectorstore_query_duration_bucket{store="cache"}[5m]))

# Documents per cache hit (efficiency)
rate(vectorstore_documents_retrieved_total{source="cache"}[5m]) /
rate(vectorstore_cache_hits_total[5m])
```

## Grafana Dashboard

### Recommended Panels

1. **Cache Hit Rate (Gauge)**
   - Formula: `hits / (hits + misses) * 100`
   - Thresholds: Red < 60%, Yellow 60-80%, Green > 80%

2. **Query Latency Comparison (Time Series)**
   - Line chart: cache vs. primary store query times
   - Show p50, p95, p99 percentiles

3. **Cache Operations (Counter)**
   - Stacked area chart: hits, misses, warming success/failures

4. **Documents Served (Counter)**
   - Pie chart: cache vs. primary source distribution

5. **Cache Efficiency (Stat)**
   - Single stat: Average documents per cache hit

## Troubleshooting

### High Cache Miss Rate

**Symptoms:** `vectorstore.cache.misses` increasing, hit rate < 60%

**Possible Causes:**
1. Cache multiplier too low (increase in `application-cloud.properties`)
2. High query diversity (many unique queries)
3. Cache eviction due to memory pressure

**Actions:**
- Review `vectorstore.cache.multiplier` setting
- Check GemFire memory allocation
- Analyze query patterns in application logs

### Cache Warming Failures

**Symptoms:** `vectorstore.cache.warming.failures` > 0

**Possible Causes:**
1. GemFire service unavailable
2. Network connectivity issues
3. GemFire capacity exceeded

**Actions:**
- Check Cloud Foundry service binding: `cf service imc-cache`
- Review GemFire logs: `cf logs imc-cache --recent`
- Verify cache capacity and eviction policies

### Slow Cache Queries

**Symptoms:** `vectorstore.query.duration{store="cache"}` similar to primary

**Possible Causes:**
1. GemFire instance under-provisioned
2. Network latency to GemFire service
3. Large result sets

**Actions:**
- Scale up GemFire service in Cloud Foundry
- Check network latency with ping/trace
- Review query patterns and topK values

## Architecture Integration

The metrics are collected automatically by the `CachingVectorStore` decorator:

```java
// Metrics recorded on cache hit
metrics.recordCacheHit(cachedResult.size());

// Metrics recorded on cache miss
metrics.recordCacheMiss();
metrics.recordPrimaryRetrieval(documentsToReturn);

// Metrics recorded on cache warming
metrics.recordCacheWarmingSuccess(primaryResult.size());
metrics.recordCacheWarmingFailure(); // on exception

// Query timers
metrics.recordCacheQueryTime(durationMillis);
metrics.recordPrimaryQueryTime(durationMillis);
```

## Best Practices

1. **Set Up Alerts:** Configure alerts for cache warming failures and low hit rates
2. **Monitor Trends:** Track metrics over time to identify degradation
3. **Capacity Planning:** Use document count metrics to plan GemFire capacity
4. **Performance Baseline:** Establish baseline query times for comparison
5. **Regular Review:** Weekly review of cache performance metrics

## Related Documentation

- [CACHE.md](CACHE.md) - Cache implementation specification
- [Spring Boot Actuator Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics)
- [Micrometer Documentation](https://micrometer.io/docs)
