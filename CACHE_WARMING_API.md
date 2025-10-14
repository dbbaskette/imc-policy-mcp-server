# Cache Warming API

## Overview

The cache warming API allows you to proactively load all documents for a customer into the GemFire cache. This is useful when a user logs in - you can trigger cache warming immediately to ensure all subsequent queries are fast.

## Endpoints

### 1. Warm Cache for Customer

**Endpoint:** `POST /api/cache/warm/{customerId}`

**Description:** Fetches ALL documents for the specified customer from Postgres and loads them into GemFire cache.

**Example:**
```bash
# Warm cache for customer 1001
curl -X POST https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/api/cache/warm/1001
```

**Success Response:**
```json
{
  "success": true,
  "message": "Cache warming completed successfully",
  "customerId": 100001,
  "documentsWarmed": 7,
  "cacheStats": {
    "totalWarmingOperations": 3,
    "totalDocumentsCached": 21,
    "cacheHitRate": "45.5%"
  }
}
```

**Response Fields:**
- `documentsWarmed`: Number of documents added to cache in this operation
- `cacheStats.totalWarmingOperations`: Total successful warming operations since startup
- `cacheStats.totalDocumentsCached`: Total documents cached across all operations
- `cacheStats.cacheHitRate`: Current cache hit rate percentage

**Error Response (cache disabled):**
```json
{
  "success": false,
  "message": "Cache warming is disabled - no credentials configured",
  "customerId": 1001
}
```

**Error Response (warming failed):**
```json
{
  "success": false,
  "message": "Cache warming failed - no documents warmed",
  "customerId": 1001
}
```

### 2. Get Cache Statistics

**Endpoint:** `GET /api/cache/stats`

**Description:** Returns cache status and hit rate summary.

**Example:**
```bash
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/api/cache/stats
```

**Response:**
```json
{
  "cacheEnabled": true,
  "cacheHitRate": "87.50%",
  "metricsEndpoint": "/actuator/metrics/vectorstore.*",
  "message": "Use /actuator/metrics for detailed metrics (hits, misses, timing, etc.)"
}
```

## Detailed Metrics

For detailed operational metrics, use Spring Boot Actuator:

```bash
# Cache hits
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.cache.hits

# Cache misses
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.cache.misses

# Cache warming successes
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.cache.warming.success

# Documents cached
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.documents.cached

# Query timing
curl https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/actuator/metrics/vectorstore.query.duration
```

## Integration Example

### On User Login

When a user logs in to your client application, trigger cache warming immediately:

```javascript
// User login handler
async function handleUserLogin(userId, customerId) {
  // Authenticate user...

  // Proactively warm cache for this customer
  try {
    const response = await fetch(
      `https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/api/cache/warm/${customerId}`,
      { method: 'POST' }
    );

    const result = await response.json();
    if (result.success) {
      console.log(`Cache warmed for customer ${customerId}`);
    } else {
      console.warn(`Cache warming failed: ${result.message}`);
    }
  } catch (error) {
    console.error('Cache warming request failed:', error);
    // Non-critical - queries will still work, just slower on first access
  }

  // Continue with normal login flow...
}
```

### Background Warming

You can also warm the cache in the background for frequently accessed customers:

```javascript
// Warm cache for top 10 most active customers every hour
async function warmFrequentCustomers() {
  const topCustomers = await getTopActiveCustomers(10);

  for (const customerId of topCustomers) {
    try {
      await fetch(
        `https://imc-policy-mcp-server.apps.tas-ndc.kuhn-labs.com/api/cache/warm/${customerId}`,
        { method: 'POST' }
      );
    } catch (error) {
      console.error(`Failed to warm cache for customer ${customerId}:`, error);
    }

    // Rate limit - wait 1 second between customers
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
}

// Schedule to run every hour
setInterval(warmFrequentCustomers, 60 * 60 * 1000);
```

## Benefits

1. **Faster First Query**: User's first query is just as fast as subsequent queries
2. **Better UX**: No "cold start" lag when user starts searching
3. **Complete Coverage**: All customer documents are cached, not just similarity matches
4. **Simpler Logic**: No need for complex similarity-based cache warming
5. **Proactive Control**: You decide when to warm the cache, not reactive to cache misses

## Technical Details

### How It Works

1. **SQL Query**: Fetches all documents from Postgres where `metadata->>'refnum1' = customerId`
2. **No Vector Similarity**: Simple metadata filter - no expensive embedding calculations
3. **Direct GemFire API**: Uses authenticated REST API to insert documents
4. **Batch Processing**: Each document is inserted individually with proper error handling
5. **Metrics Tracking**: Records warming success/failure and document counts

### Performance

- Typical warming time for 12 documents: **< 1 second**
- No impact on primary Postgres database (read-only query)
- Cache warming runs asynchronously from user queries
- Failed warmings are logged but don't block normal operation

### Security

- Customer data isolation enforced via `refnum1` metadata filter
- Only documents for the specified customer are cached
- GemFire authentication required (Basic Auth)
- No cross-customer data leakage possible
