# GemFire Cache Implementation - Deployment Notes

## Summary

Successfully implemented comprehensive cache metrics and infrastructure for the GemFire proactive read-through cache layer. The application is fully functional with graceful degradation when cache authentication fails. All queries work correctly via Postgres fallback.

## Known Limitations

### ⚠️ GemFire VectorDB Authentication Not Supported

**Issue:** Spring AI's `GemFireVectorStore` (version 1.0.0-M4) does not support authentication. The builder API doesn't expose credential configuration methods, and the WebClient is created internally without dependency injection.

**Impact:** The GemFire cache cannot authenticate with Cloud Foundry's GemFire VectorDB service, resulting in `401 Unauthorized` errors. All cache operations gracefully fall back to the Postgres primary store.

**Application Status:** ✅ **Fully Functional**
- All RAG queries work correctly
- MCP integration working
- Graceful degradation on cache failure
- Cache stats dashboard available at `/cache-stats`

**Attempted Solutions:**
- ✅ SSL configuration (working - using HTTPS)
- ✅ Environment-based configuration (working - correct host/port/credentials extracted)
- ❌ WebClient.Builder bean injection (not picked up by GemFireVectorStore)
- ❌ Custom wrapper delegation (still uses internal WebClient without auth)

**Future Workarounds:**
1. Wait for Spring AI to add authentication support
2. Create custom VectorStore using GemFire REST API directly
3. Use HTTP proxy/service mesh for authentication
4. Disable authentication on GemFire service (not recommended)

**Reference:** GemFire VectorDB REST API requires `Authorization: Basic <base64(username:password)>` header as shown in [Tanzu GemFire docs](https://techdocs.broadcom.com).

## Changes Made

### 1. Metrics Implementation
- **[VectorCacheMetrics.java](src/main/java/com/insurancemegacorp/policymcpserver/service/VectorCacheMetrics.java)** - Micrometer-based metrics collector
- **[CachingVectorStore.java](src/main/java/com/insurancemegacorp/policymcpserver/service/CachingVectorStore.java)** - Integrated metrics recording at all key points
- **[VectorStoreConfig.java](src/main/java/com/insurancemegacorp/policymcpserver/config/VectorStoreConfig.java)** - Wired metrics into caching vector store

### 2. GemFire Service Binding
- **[GemFireServiceBindingProcessor.java](src/main/java/com/insurancemegacorp/policymcpserver/config/GemFireServiceBindingProcessor.java)** - Custom environment post-processor to extract GemFire connection details from Cloud Foundry `p-cloudcache` service binding
- **[spring.factories](src/main/resources/META-INF/spring.factories)** - Registered the environment post-processor
- **[application-cloud.properties](src/main/resources/application-cloud.properties)** - Excluded GemFire auto-configuration to prevent bean conflicts

### 3. Configuration
- **[application.properties](src/main/resources/application.properties)** - Exposed metrics endpoint via Actuator
- Auto-configured from Cloud Foundry service binding `imc-cache` via `VCAP_SERVICES`

### 4. Documentation
- **[METRICS.md](METRICS.md)** - Comprehensive metrics documentation including usage, alerting, and troubleshooting

## Issues Resolved

### Issue 1: Bean Naming Conflict
**Problem:** GemFire auto-configuration conflicted with custom bean definition
```
Bean 'gemfireVectorStore' already defined
```

**Solution:** Excluded auto-configuration in `application-cloud.properties`:
```properties
spring.autoconfigure.exclude=org.springframework.ai.vectorstore.gemfire.autoconfigure.GemFireVectorStoreAutoConfiguration
```

### Issue 2: GemFire Connection to localhost
**Problem:** GemFire VectorStore defaulted to `http://localhost:8080` instead of using Cloud Foundry service binding

**Solution:** Created `GemFireServiceBindingProcessor` to:
1. Extract `vectordb` URL from `p-cloudcache` service credentials
2. Parse host and port from URL
3. Configure Spring properties before bean creation

**Service URL:**
```
https://gemfire-server-5f442f73-ba7e-43fe-940e-de74494fc59a.sys.tas-ndc.kuhn-labs.com/gemfire-vectordb/v1
```

### Issue 3: GemFire Schema Initialization Failure
**Problem:** Bean creation failed when trying to initialize GemFire schema on startup

**Solution:** Set `initializeSchema(false)` to defer schema operations until first use

## Deployment Verification

### Application Status
```bash
cf app imc-policy-mcp-server
```
✅ Status: Running
✅ Instances: 1/1
✅ Memory: 356M of 1G

### Service Bindings
```bash
cf services
```
- ✅ `embed-db` (Postgres) - Bound
- ✅ `imc-cache` (GemFire) - Bound
- ✅ `multi-modal` (GenAI) - Bound

### Startup Logs
```
2025-10-13T17:11:57.865Z  INFO --- Configuring GemFire vector store for caching:
  gemfire-server-5f442f73-ba7e-43fe-940e-de74494fc59a.sys.tas-ndc.kuhn-labs.com:443
  (dimensions=768)

2025-10-13T17:11:58.467Z  INFO --- CachingVectorStore initialized with cache multiplier: 5

2025-10-13T17:12:00.094Z  INFO --- Started ImcPolicyMcpServerApplication in 8.647 seconds
```

### Metrics Verification
```bash
# List all vectorstore metrics
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics | \
  jq '.names[] | select(contains("vectorstore"))'
```

**Available Metrics:**
- `vectorstore.cache.hits`
- `vectorstore.cache.misses`
- `vectorstore.cache.warming.success`
- `vectorstore.cache.warming.failures`
- `vectorstore.documents.cached`
- `vectorstore.documents.retrieved`
- `vectorstore.query.duration`

### Example Metric Query
```bash
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.cache.hits
```

```json
{
  "name": "vectorstore.cache.hits",
  "description": "Number of cache hits",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 0.0
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

## Architecture

### Cache Flow
```
Query Request
    ↓
CachingVectorStore (with metrics)
    ↓
├─→ GemFire Cache (try first)
│   ├─→ HIT → record metrics → return documents
│   └─→ MISS → continue to primary
│
└─→ Postgres Primary Store
    ├─→ Query with multiplier (e.g., 4 * 5 = 20 docs)
    ├─→ Warm cache with all results
    ├─→ Record metrics
    └─→ Return requested docs (e.g., 4)
```

### GemFire Service Binding Flow
```
Cloud Foundry
    ↓
VCAP_SERVICES (p-cloudcache)
    ↓
GemFireServiceBindingProcessor
    ├─→ Extract urls.vectordb
    ├─→ Parse host and port
    └─→ Set spring.ai.vectorstore.gemfire.*
    ↓
VectorStoreConfig
    ├─→ @Value injected from properties
    └─→ GemFireVectorStore.builder()
        .host(gemfireHost)
        .port(gemfirePort)
```

## Monitoring & Alerting

### Key Metrics to Monitor

1. **Cache Hit Rate**
   ```
   rate = hits / (hits + misses)
   ```
   - Target: > 80%
   - Alert: < 60%

2. **Cache Warming Failures**
   - Target: 0 per hour
   - Alert: > 5 per hour

3. **Query Latency**
   - Cache: p95 < 50ms
   - Primary: p95 < 200ms

### Grafana Dashboard Panels
See [METRICS.md](METRICS.md#grafana-dashboard) for recommended dashboard configuration.

## Next Steps

1. **Monitor cache performance** over the next few days
2. **Tune cache multiplier** based on hit rate and memory usage
3. **Set up Prometheus scraping** if not already configured
4. **Create Grafana dashboards** for visualization
5. **Configure alerts** for cache failures and low hit rates

## Troubleshooting

### Application Won't Start
**Check:** Bean definition conflicts
```bash
cf logs imc-policy-mcp-server --recent | grep "BeanDefinitionOverrideException"
```

**Solution:** Verify `spring.autoconfigure.exclude` is set in cloud profile

### GemFire Connection Errors
**Check:** Service binding configuration
```bash
cf env imc-policy-mcp-server | grep -A 10 "p-cloudcache"
```

**Solution:** Verify `imc-cache` service is bound and credentials contain `urls.vectordb`

### Metrics Not Available
**Check:** Actuator endpoints
```bash
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator
```

**Solution:** Verify `management.endpoints.web.exposure.include=health,info,metrics` in properties

## Related Documentation

- [CACHE.md](CACHE.md) - Original cache implementation specification
- [METRICS.md](METRICS.md) - Detailed metrics documentation
- [README.md](README.md) - Project overview

## Build Information

- **Version:** 0.0.2
- **Deployed:** 2025-10-13 13:12:00 UTC
- **Revision:** 33
- **Java Version:** 21.0.7
- **Spring Boot:** 3.3.6
- **Spring AI:** 1.1.0-SNAPSHOT
