# GemFire VectorDB Setup Guide

## Current Status

✅ **Application Running:** Successfully deployed with GemFire cache integration
⚠️ **VectorDB Not Enabled:** GemFire VectorDB feature must be enabled on the service for caching to work
✅ **Graceful Degradation:** Application runs fine without cache, using only Postgres

## What's Working

1. **Application Startup:** App starts successfully with all services bound
2. **GemFire Connection:** Properly configured to connect to GemFire service
3. **Service Binding:** Auto-configured from Cloud Foundry `imc-cache` service
4. **Metrics Available:** All cache metrics endpoints are configured and ready
5. **Fallback Behavior:** Queries work using Postgres when cache is unavailable

## What's Missing

**GemFire VectorDB is NOT enabled** on the `imc-cache` service. This causes:
- 400 Bad Request errors when trying to query the cache
- All queries fall back to Postgres (no caching)
- Cache metrics show 0 hits (because cache isn't being used)

## How to Enable GemFire VectorDB

### Step 1: Update the GemFire Service

Enable the VectorDB feature on your GemFire service:

```bash
cf update-service imc-cache -c '{"enable_vector_db": true}'
```

**Note:** The exact parameter name may vary depending on your GemFire version. Try these alternatives if the above doesn't work:

```bash
# Alternative 1
cf update-service imc-cache -c '{"vectordb_enabled": true}'

# Alternative 2
cf update-service imc-cache -c '{"vector-db": {"enabled": true}}'
```

### Step 2: Wait for Service Update

Check the service status:

```bash
cf service imc-cache
```

Wait until status shows: `update succeeded`

### Step 3: Restart the Application

Restart to pick up the updated service configuration:

```bash
cf restage imc-policy-mcp-server
```

Or if you prefer a full restart:

```bash
cf restart imc-policy-mcp-server
```

### Step 4: Verify VectorDB is Working

Check the logs for successful cache operations:

```bash
cf logs imc-policy-mcp-server --recent | grep -i "cache"
```

You should see:
- `Cache HIT: Found X documents in cache`
- NO more "400 Bad Request" errors

Test the metrics:

```bash
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.cache.hits
```

## Alternative: Manual Index Creation

If you can't enable VectorDB via service update, you may need to create the index manually using `gfsh`:

### Connect to GemFire

```bash
# Get the gfsh connection string from service key
cf service-key imc-cache test-key

# Look for "gfsh_login_string" in the output
# It will look like:
connect --url=https://cloudcache-xxx.sys.xxx.com/gemfire/v1 \
  --user=cluster_operator_xxx \
  --password=xxx \
  --skip-ssl-validation
```

### Create the Vector Index

Once connected to gfsh:

```gfsh
# Create the vector index region
create region --name=spring-ai-gemfire-index --type=PARTITION

# Note: VectorDB indexes may require additional configuration
# specific to your GemFire version. Consult GemFire docs for details.
```

## Verification Checklist

After enabling VectorDB, verify everything is working:

- [  ] Service update completed successfully
- [  ] Application restarted/restaged
- [  ] No 400 errors in logs
- [  ] Cache metrics show non-zero hits after queries
- [  ] Query performance improved (check metrics)

## Troubleshooting

### Still Getting 400 Errors

**Check service credentials:**
```bash
cf env imc-policy-mcp-server | grep -A 30 "p-cloudcache"
```

Verify the `urls.vectordb` endpoint exists.

### VectorDB Feature Not Available

Your GemFire plan may not support VectorDB. Check with your platform team or:

```bash
cf marketplace -e p-cloudcache
```

Look for VectorDB in the plan features.

### Cache Hits Still Zero

1. **Make some queries** - the cache needs data first:
   ```bash
   curl "https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/tools/prompt-policy-documents?customerId=100002"
   ```

2. **Check cache warming**:
   ```bash
   cf logs imc-policy-mcp-server | grep "Warming cache"
   ```

3. **Verify metrics**:
   ```bash
   curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.cache.warming.success
   ```

## Current Configuration

**GemFire Service:** `imc-cache`
**Service Plan:** `extra-small`
**VectorDB URL:** `https://gemfire-server-5f442f73-ba7e-43fe-940e-de74494fc59a.sys.tas-ndc.kuhn-labs.com/gemfire-vectordb/v1`
**Index Name:** `spring-ai-gemfire-index`
**Cache Multiplier:** `5` (fetches 5x documents on cache miss)

## Architecture Flow

```
Client Request
    ↓
CachingVectorStore
    ↓
├─→ Try GemFire Cache
│   └─→ If VectorDB enabled:
│       ├─→ HIT → return documents + metrics
│       └─→ MISS → query Postgres with 5x multiplier
│                   warm cache
│                   return requested documents
│   └─→ If VectorDB NOT enabled:
│       └─→ 400 error → fall back to Postgres
│
└─→ Postgres Primary (always authoritative)
```

## Next Steps

1. **Enable VectorDB** on the GemFire service (see Step 1 above)
2. **Restart the application** after service update completes
3. **Monitor metrics** to see cache performance improvement
4. **Tune cache multiplier** based on hit rates (configured in `application-cloud.properties`)

## Related Documentation

- [CACHE.md](CACHE.md) - Cache implementation specification
- [METRICS.md](METRICS.md) - Metrics documentation and monitoring
- [DEPLOYMENT_NOTES.md](DEPLOYMENT_NOTES.md) - Deployment history and troubleshooting

## Support

If you encounter issues:

1. Check GemFire service health: `cf service imc-cache`
2. Review application logs: `cf logs imc-policy-mcp-server --recent`
3. Verify service binding: `cf env imc-policy-mcp-server`
4. Check metrics endpoint: `/actuator/metrics`

For GemFire-specific issues, consult:
- [VMware Tanzu GemFire Docs](https://techdocs.broadcom.com/us/en/vmware-tanzu/data-solutions/tanzu-gemfire-on-cloud-foundry/2-1.html)
- GemFire VectorDB documentation (if available for your version)
